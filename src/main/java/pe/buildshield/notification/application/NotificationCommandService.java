package pe.buildshield.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.buildshield.notification.domain.model.Audience;
import pe.buildshield.notification.domain.model.DispatchInfo;
import pe.buildshield.notification.domain.model.LogisticsDirectory;
import pe.buildshield.notification.domain.model.Notification;
import pe.buildshield.notification.domain.model.NotificationRepository;
import pe.buildshield.notification.domain.model.NotificationType;
import pe.buildshield.notification.domain.model.Recipient;
import pe.buildshield.notification.domain.model.RecipientDirectory;
import pe.buildshield.notification.shared.error.ResourceNotFoundException;
import pe.buildshield.notification.shared.messaging.EventEnvelope;
import pe.buildshield.notification.shared.tenant.TenantContext;
import pe.buildshield.notification.shared.tenant.TenantInfo;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static pe.buildshield.notification.application.NotificationEvents.*;

/**
 * Crea los avisos a partir de los eventos (US43) y mantiene las copias locales que necesita para decidir a quién
 * avisar:
 *
 * <ul>
 *   <li>Despacho en tránsito: encargados de obra asignados a la obra destino.</li>
 *   <li>Despacho anulado: encargados de obra de la obra destino.</li>
 *   <li>Incidencia en ruta: encargados de obra de la obra destino y administradores.</li>
 *   <li>Recepción confirmada con materiales fuera de tolerancia: encargados de almacén del almacén de origen y
 *       administradores.</li>
 *   <li>Discrepancia respondida: quien la abrió; escalada: administradores; resuelta o aceptada: encargados de
 *       almacén del almacén de origen y quien la abrió.</li>
 * </ul>
 *
 * Nunca se avisa a quien provocó el evento. Cada aviso va también por correo después del commit.
 */
@Service
public class NotificationCommandService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCommandService.class);

    private static final Map<String, String> INCIDENT_TYPES = Map.of(
            "DELAY", "Retraso",
            "VEHICLE_FAILURE", "Falla del vehículo",
            "DAMAGE", "Daño de la carga",
            "ACCIDENT", "Accidente",
            "ROUTE_CHANGE", "Cambio de ruta",
            "OTHER", "Otra");

    private final NotificationRepository notifications;
    private final RecipientDirectory recipients;
    private final LogisticsDirectory logistics;
    private final EmailPort email;
    private final Clock clock;

    public NotificationCommandService(NotificationRepository notifications, RecipientDirectory recipients,
            LogisticsDirectory logistics, EmailPort email, Clock clock) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.logistics = logistics;
        this.email = email;
        this.clock = clock;
    }

    /**
     * Aplica un evento de otro contexto: actualiza las copias locales y crea los avisos que correspondan.
     *
     * @return cuántos avisos se crearon
     */
    @Transactional
    public int handle(EventEnvelope event) {
        UUID organization = event.organizationId();
        JsonNode payload = event.payload() == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance()
                : event.payload();
        rememberDispatch(organization, payload);
        return switch (event.eventType()) {
            case USER_CREATED, USER_UPDATED -> {
                UUID userId = uuid(payload, "userId");
                if (userId != null) {
                    recipients.upsertUser(organization, userId, text(payload, "fullName"), text(payload, "email"),
                            payload.path("role").asText(""), payload.path("active").asBoolean(true));
                }
                yield 0;
            }
            case LOCATION_REGISTERED, LOCATION_UPDATED -> {
                UUID locationId = uuid(payload, "locationId");
                if (locationId != null && text(payload, "name") != null) {
                    logistics.upsertLocation(organization, locationId, payload.path("type").asText(""),
                            text(payload, "name"));
                }
                yield 0;
            }
            case STAFF_ASSIGNED -> {
                UUID assignmentId = uuid(payload, "assignmentId");
                UUID userId = uuid(payload, "userId");
                UUID locationId = uuid(payload, "locationId");
                if (assignmentId != null && userId != null && locationId != null) {
                    recipients.assign(organization, assignmentId, userId, locationId,
                            payload.path("locationType").asText(""));
                }
                yield 0;
            }
            case STAFF_UNASSIGNED -> {
                UUID assignmentId = uuid(payload, "assignmentId");
                if (assignmentId != null) {
                    recipients.unassign(assignmentId);
                }
                yield 0;
            }
            case DISPATCH_DEPARTED -> dispatchDeparted(event, payload);
            case DISPATCH_CANCELLED -> dispatchCancelled(event, payload);
            case TRANSIT_INCIDENT_REPORTED -> transitIncident(event, payload);
            case RECEPTION_CONFIRMED -> receptionConfirmed(event, payload);
            case DISCREPANCY_RESPONDED, DISCREPANCY_ESCALATED, DISCREPANCY_RESOLVED, DISCREPANCY_ACCEPTED ->
                    discrepancyChanged(event, payload);
            default -> 0;
        };
    }

    /** Marca como leída una notificación del usuario del token; la de otro usuario no existe para él (404). */
    @Transactional
    public void markRead(UUID notificationId) {
        TenantInfo requester = TenantContext.require();
        Notification notification = notifications.findById(notificationId)
                .filter(found -> found.belongsTo(requester.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("NOTIFICATION_NOT_FOUND",
                        "La notificación " + notificationId + " no existe"));
        if (!notification.isRead()) {
            notification.markRead(clock.instant());
            notifications.save(notification);
        }
    }

    /** @return cuántas se marcaron */
    @Transactional
    public int markAllRead() {
        return notifications.markAllRead(TenantContext.require().userId(), clock.instant());
    }

    private int dispatchDeparted(EventEnvelope event, JsonNode payload) {
        DispatchInfo dispatch = dispatch(event, payload);
        if (dispatch == null || dispatch.worksiteId() == null) {
            return 0;
        }
        Audience audience = new Audience(event.actorId())
                .add(recipients.assignedTo(event.organizationId(), dispatch.worksiteId(), Recipient.SITE_MANAGER));
        String worksite = locationName(event, dispatch.worksiteId(), "la obra");
        String warehouse = locationName(event, dispatch.warehouseId(), "el almacén");
        return notify(event, audience, NotificationType.DISPATCH_IN_TRANSIT, "Despacho en camino a " + worksite,
                "El despacho " + dispatch.reference() + " salió de " + warehouse + " hacia " + worksite
                        + ". Prepárate para recibirlo.",
                "/reception/" + dispatch.dispatchId());
    }

    private int dispatchCancelled(EventEnvelope event, JsonNode payload) {
        DispatchInfo dispatch = dispatch(event, payload);
        if (dispatch == null || dispatch.worksiteId() == null) {
            return 0;
        }
        Audience audience = new Audience(event.actorId())
                .add(recipients.assignedTo(event.organizationId(), dispatch.worksiteId(), Recipient.SITE_MANAGER));
        String reason = text(payload, "reason");
        return notify(event, audience, NotificationType.DISPATCH_CANCELLED, "Despacho anulado",
                "El despacho " + dispatch.reference() + " hacia " + locationName(event, dispatch.worksiteId(), "la obra")
                        + " fue anulado" + (reason == null ? "." : ": " + reason),
                "/dispatches/" + dispatch.dispatchId());
    }

    private int transitIncident(EventEnvelope event, JsonNode payload) {
        DispatchInfo dispatch = dispatch(event, payload);
        if (dispatch == null) {
            return 0;
        }
        Audience audience = new Audience(event.actorId());
        if (dispatch.worksiteId() != null) {
            audience.add(recipients.assignedTo(event.organizationId(), dispatch.worksiteId(), Recipient.SITE_MANAGER));
        }
        audience.add(recipients.withRole(event.organizationId(), Recipient.ADMINISTRATOR));
        String type = text(payload, "typeLabel");
        if (type == null) {
            String code = payload.path("type").asText("OTHER");
            type = INCIDENT_TYPES.getOrDefault(code, code);
        }
        String observation = text(payload, "observation");
        return notify(event, audience, NotificationType.TRANSIT_INCIDENT, "Incidencia en ruta: " + type,
                "Despacho " + dispatch.reference() + (dispatch.worksiteId() == null ? ""
                        : " hacia " + locationName(event, dispatch.worksiteId(), "la obra"))
                        + (observation == null ? "." : ": " + observation),
                "/dispatches/" + dispatch.dispatchId());
    }

    private int receptionConfirmed(EventEnvelope event, JsonNode payload) {
        int outOfTolerance = 0;
        for (JsonNode line : payload.path("lines")) {
            if (line.has("withinTolerance") && !line.path("withinTolerance").asBoolean(true)) {
                outOfTolerance++;
            }
        }
        DispatchInfo dispatch = dispatch(event, payload);
        if (outOfTolerance == 0 || dispatch == null) {
            return 0;
        }
        Audience audience = new Audience(event.actorId());
        if (dispatch.warehouseId() != null) {
            audience.add(recipients.assignedTo(event.organizationId(), dispatch.warehouseId(),
                    Recipient.WAREHOUSE_MANAGER));
        }
        audience.add(recipients.withRole(event.organizationId(), Recipient.ADMINISTRATOR));
        String materials = outOfTolerance == 1 ? "1 material fuera" : outOfTolerance + " materiales fuera";
        return notify(event, audience, NotificationType.RECEPTION_WITH_DISCREPANCIES, "Recepción con discrepancias",
                "La recepción del despacho " + dispatch.reference() + " en "
                        + locationName(event, dispatch.worksiteId(), "la obra") + " tiene " + materials
                        + " de la tolerancia.",
                "/discrepancies?dispatchId=" + dispatch.dispatchId());
    }

    private int discrepancyChanged(EventEnvelope event, JsonNode payload) {
        UUID discrepancyId = uuid(payload, "discrepancyId");
        if (discrepancyId == null && event.aggregateId() != null) {
            discrepancyId = parse(event.aggregateId());
        }
        if (discrepancyId == null) {
            return 0;
        }
        UUID openedBy = uuid(payload, "openedBy");
        UUID warehouseId = uuid(payload, "warehouseId");
        Audience audience = new Audience(event.actorId());
        NotificationType type;
        String title;
        switch (event.eventType()) {
            case DISCREPANCY_RESPONDED -> {
                type = NotificationType.DISCREPANCY_RESPONDED;
                title = "Discrepancia respondida por el almacén";
                audience.add(opener(event, openedBy));
            }
            case DISCREPANCY_ESCALATED -> {
                type = NotificationType.DISCREPANCY_ESCALATED;
                title = "Discrepancia escalada: requiere tu decisión";
                audience.add(recipients.withRole(event.organizationId(), Recipient.ADMINISTRATOR));
            }
            case DISCREPANCY_RESOLVED -> {
                type = NotificationType.DISCREPANCY_RESOLVED;
                title = "Discrepancia resuelta";
                addWarehouseManagers(event, audience, warehouseId);
                audience.add(opener(event, openedBy));
            }
            default -> {
                type = NotificationType.DISCREPANCY_ACCEPTED;
                title = "Discrepancia aceptada por la obra";
                addWarehouseManagers(event, audience, warehouseId);
                audience.add(opener(event, openedBy));
            }
        }
        String material = text(payload, "materialName") != null ? text(payload, "materialName") : text(payload, "sku");
        String manifest = text(payload, "manifestCode");
        String detail = text(payload, "text");
        String body = (material == null ? "Material" : material)
                + (manifest == null ? "" : " del despacho " + manifest)
                + (detail == null ? "." : ": " + detail);
        return notify(event, audience, type, title, body, "/discrepancies/" + discrepancyId);
    }

    private void addWarehouseManagers(EventEnvelope event, Audience audience, UUID warehouseId) {
        if (warehouseId != null) {
            audience.add(recipients.assignedTo(event.organizationId(), warehouseId, Recipient.WAREHOUSE_MANAGER));
        }
    }

    /** Quien abrió la discrepancia; si la copia local todavía no lo conoce, igual recibe el aviso en la bandeja. */
    private Recipient opener(EventEnvelope event, UUID openedBy) {
        if (openedBy == null) {
            return null;
        }
        return recipients.find(event.organizationId(), openedBy)
                .orElse(new Recipient(openedBy, null, null, null, true));
    }

    private int notify(EventEnvelope event, Audience audience, NotificationType type, String title, String body,
            String link) {
        if (audience.isEmpty()) {
            log.debug("Evento {} sin destinatarios", event.eventType());
            return 0;
        }
        Instant now = clock.instant();
        List<Recipient> targets = audience.recipients();
        notifications.saveAll(targets.stream().map(recipient -> Notification.create(event.organizationId(),
                recipient.userId(), type, title, body, link, event.eventId(), now)).toList());
        sendEmailsAfterCommit(targets, title, body);
        return targets.size();
    }

    private void sendEmailsAfterCommit(List<Recipient> targets, String subject, String body) {
        Runnable send = () -> targets.stream().filter(recipient -> recipient.email() != null).forEach(recipient -> {
            try {
                email.send(recipient.email(), subject, body);
            } catch (RuntimeException ex) {
                log.warn("No se pudo enviar el correo a {}: {}", recipient.userId(), ex.getMessage());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private void rememberDispatch(UUID organization, JsonNode payload) {
        UUID dispatchId = uuid(payload, "dispatchId");
        if (dispatchId == null) {
            return;
        }
        String manifest = text(payload, "manifestCode");
        UUID worksite = uuid(payload, "worksiteId");
        UUID warehouse = uuid(payload, "warehouseId");
        if (manifest != null || worksite != null || warehouse != null) {
            logistics.upsertDispatch(organization, dispatchId, manifest, worksite, warehouse);
        }
    }

    private DispatchInfo dispatch(EventEnvelope event, JsonNode payload) {
        UUID dispatchId = uuid(payload, "dispatchId");
        if (dispatchId == null) {
            return null;
        }
        return logistics.dispatch(event.organizationId(), dispatchId)
                .orElse(new DispatchInfo(dispatchId, text(payload, "manifestCode"), uuid(payload, "worksiteId"),
                        uuid(payload, "warehouseId")));
    }

    private String locationName(EventEnvelope event, UUID locationId, String fallback) {
        if (locationId == null) {
            return fallback;
        }
        return logistics.locationName(event.organizationId(), locationId).orElse(fallback);
    }

    static UUID uuid(JsonNode payload, String field) {
        return payload.hasNonNull(field) ? parse(payload.get(field).asText()) : null;
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static String text(JsonNode payload, String field) {
        return payload.hasNonNull(field) && !payload.get(field).asText().isBlank() ? payload.get(field).asText() : null;
    }
}
