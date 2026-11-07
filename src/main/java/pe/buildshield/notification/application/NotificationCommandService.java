package pe.buildshield.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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
import pe.buildshield.notification.domain.model.NotificationTemplate;
import pe.buildshield.notification.domain.model.NotificationType;
import pe.buildshield.notification.domain.model.Recipient;
import pe.buildshield.notification.domain.model.RecipientDirectory;
import pe.buildshield.notification.domain.model.RecipientSetting;
import pe.buildshield.notification.domain.model.RecipientSettingRepository;
import pe.buildshield.notification.shared.error.ResourceNotFoundException;
import pe.buildshield.notification.shared.messaging.EventEnvelope;
import pe.buildshield.notification.shared.tenant.TenantContext;
import pe.buildshield.notification.shared.tenant.TenantInfo;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

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
 *   <li>Enlaces: {@code /reception/{dispatchId}}, {@code /discrepancies/{id}} y {@code /dispatches/{id}}; este último
 *       se cambia por {@code /dispatches/{id}/custody} para los encargados de obra.</li>
 *   <li>Discrepancia abierta: encargados de obra asignados a la obra.</li>
 *   <li>Discrepancia respondida: quien la abrió; escalada: administradores; resuelta o aceptada: encargados de
 *       almacén del almacén de origen y quien la abrió.</li>
 *   <li>Pedido aprobado: encargados de obra y encargados de almacén.</li>
 *   <li>Reserva de stock rechazada: encargados de obra y administradores.</li>
 *   <li>Pago exitoso / fallido / expirado: administradores.</li>
 *   <li>Recuperación de contraseña: correo al destinatario indicado.</li>
 * </ul>
 *
 * Cada aviso va también por correo después del commit protegido con Circuit Breaker.
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
    private final RecipientSettingRepository settings;
    private final EmailPort email;
    private final Clock clock;

    public NotificationCommandService(NotificationRepository notifications, RecipientDirectory recipients,
            LogisticsDirectory logistics, RecipientSettingRepository settings, EmailPort email, Clock clock) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.logistics = logistics;
        this.settings = settings;
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
            case DISCREPANCY_OPENED -> discrepancyOpened(event, payload);
            case ORDER_APPROVED -> orderApproved(event, payload);
            case STOCK_RESERVATION_REJECTED -> stockReservationRejected(event, payload);
            case PAYMENT_SUCCEEDED -> paymentSucceeded(event, payload);
            case PAYMENT_FAILED -> paymentFailed(event, payload);
            case SUBSCRIPTION_EXPIRED -> subscriptionExpired(event, payload);
            case PASSWORD_RESET_REQUESTED -> passwordResetRequested(event, payload);
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

    /** Reintenta el envío por correo de las notificaciones que quedaron en estado PENDING. */
    @Transactional
    public int retryPending() {
        List<Notification> pending = notifications.findPending();
        int sentCount = 0;
        for (Notification notification : pending) {
            Recipient recipient = recipients.find(notification.organizationId(), notification.recipientUserId()).orElse(null);
            if (recipient == null || recipient.email() == null) {
                continue;
            }
            try {
                email.send(recipient.email(), notification.title(), notification.body());
                notification.markSent(clock.instant());
                notifications.recordDelivery(notification);
                sentCount++;
            } catch (CallNotPermittedException ex) {
                log.warn("Circuit breaker abierto al reintentar aviso {}: {}", notification.id(), ex.getMessage());
                break;
            } catch (RuntimeException ex) {
                log.warn("No se pudo enviar correo pendiente para aviso {}: {}", notification.id(), ex.getMessage());
                notification.markFailed();
                notifications.recordDelivery(notification);
            }
        }
        return sentCount;
    }

    private int dispatchDeparted(EventEnvelope event, JsonNode payload) {
        DispatchInfo dispatch = dispatch(event, payload);
        if (dispatch == null || dispatch.worksiteId() == null) {
            return 0;
        }
        Audience audience = audience(event.actorId(), event.organizationId(), NotificationType.DISPATCH_IN_TRANSIT,
                dispatch.worksiteId(),
                () -> recipients.assignedTo(event.organizationId(), dispatch.worksiteId(), Recipient.SITE_MANAGER),
                null);
        String worksite = locationName(event, dispatch.worksiteId(), "la obra");
        String warehouse = locationName(event, dispatch.warehouseId(), "el almacén");
        NotificationTemplate.RenderedNotification rendered = NotificationTemplate.dispatchInTransit(worksite,
                warehouse, dispatch.reference(), dispatch.dispatchId());
        return notify(event, audience, NotificationType.DISPATCH_IN_TRANSIT, rendered.title(), rendered.body(),
                rendered.link());
    }

    private int dispatchCancelled(EventEnvelope event, JsonNode payload) {
        DispatchInfo dispatch = dispatch(event, payload);
        if (dispatch == null || dispatch.worksiteId() == null) {
            return 0;
        }
        Audience audience = audience(event.actorId(), event.organizationId(), NotificationType.DISPATCH_CANCELLED,
                dispatch.worksiteId(),
                () -> recipients.assignedTo(event.organizationId(), dispatch.worksiteId(), Recipient.SITE_MANAGER),
                null);
        String reason = text(payload, "reason");
        NotificationTemplate.RenderedNotification rendered = NotificationTemplate.dispatchCancelled(
                locationName(event, dispatch.worksiteId(), "la obra"), dispatch.reference(), reason,
                dispatch.dispatchId());
        return notify(event, audience, NotificationType.DISPATCH_CANCELLED, rendered.title(), rendered.body(),
                rendered.link());
    }

    private int transitIncident(EventEnvelope event, JsonNode payload) {
        DispatchInfo dispatch = dispatch(event, payload);
        if (dispatch == null) {
            return 0;
        }
        Audience audience = audience(event.actorId(), event.organizationId(), NotificationType.TRANSIT_INCIDENT,
                dispatch.worksiteId(), () -> {
                    List<Recipient> byDefault = new ArrayList<>();
                    if (dispatch.worksiteId() != null) {
                        byDefault.addAll(recipients.assignedTo(event.organizationId(), dispatch.worksiteId(),
                                Recipient.SITE_MANAGER));
                    }
                    byDefault.addAll(recipients.withRole(event.organizationId(), Recipient.ADMINISTRATOR));
                    return byDefault;
                }, null);
        String type = text(payload, "typeLabel");
        if (type == null) {
            String code = payload.path("type").asText("OTHER");
            type = INCIDENT_TYPES.getOrDefault(code, code);
        }
        String observation = text(payload, "observation");
        String worksite = dispatch.worksiteId() == null ? null : locationName(event, dispatch.worksiteId(), "la obra");
        NotificationTemplate.RenderedNotification rendered = NotificationTemplate.transitIncident(type,
                dispatch.reference(), worksite, observation, dispatch.dispatchId());
        return notify(event, audience, NotificationType.TRANSIT_INCIDENT, rendered.title(), rendered.body(),
                rendered.link());
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
        Audience audience = audience(event.actorId(), event.organizationId(),
                NotificationType.RECEPTION_WITH_DISCREPANCIES, dispatch.worksiteId(),
                () -> {
                    List<Recipient> byDefault = new ArrayList<>(warehouseManagers(event,
                            dispatch.warehouseId()));
                    byDefault.addAll(recipients.withRole(event.organizationId(), Recipient.ADMINISTRATOR));
                    return byDefault;
                }, null);
        NotificationTemplate.RenderedNotification rendered = NotificationTemplate.receptionWithDiscrepancies(
                dispatch.reference(), locationName(event, dispatch.worksiteId(), "la obra"), outOfTolerance,
                dispatch.dispatchId());
        return notify(event, audience, NotificationType.RECEPTION_WITH_DISCREPANCIES, rendered.title(),
                rendered.body(), rendered.link());
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
        UUID worksiteId = uuid(payload, "worksiteId");
        if (worksiteId == null) {
            DispatchInfo dispatch = dispatch(event, payload);
            worksiteId = dispatch == null ? null : dispatch.worksiteId();
        }
        NotificationType type;
        Supplier<List<Recipient>> byDefault;
        Recipient personal = null;
        switch (event.eventType()) {
            case DISCREPANCY_RESPONDED -> {
                type = NotificationType.DISCREPANCY_RESPONDED;
                byDefault = List::of;
                personal = opener(event, openedBy);
            }
            case DISCREPANCY_ESCALATED -> {
                type = NotificationType.DISCREPANCY_ESCALATED;
                byDefault = () -> recipients.withRole(event.organizationId(), Recipient.ADMINISTRATOR);
            }
            case DISCREPANCY_RESOLVED -> {
                type = NotificationType.DISCREPANCY_RESOLVED;
                byDefault = () -> warehouseManagers(event, warehouseId);
                personal = opener(event, openedBy);
            }
            default -> {
                type = NotificationType.DISCREPANCY_ACCEPTED;
                byDefault = () -> warehouseManagers(event, warehouseId);
                personal = opener(event, openedBy);
            }
        }
        Audience audience = audience(event.actorId(), event.organizationId(), type, worksiteId, byDefault, personal);
        String material = text(payload, "materialName") != null ? text(payload, "materialName") : text(payload, "sku");
        String manifest = text(payload, "manifestCode");
        String detail = text(payload, "text");
        NotificationTemplate.RenderedNotification rendered = NotificationTemplate.discrepancy(type, material,
                manifest, detail, discrepancyId);
        return notify(event, audience, type, rendered.title(), rendered.body(), rendered.link());
    }

    private int discrepancyOpened(EventEnvelope event, JsonNode payload) {
        UUID discrepancyId = uuid(payload, "discrepancyId");
        if (discrepancyId == null && event.aggregateId() != null) {
            discrepancyId = parse(event.aggregateId());
        }
        if (discrepancyId == null) {
            return 0;
        }
        UUID worksiteId = uuid(payload, "worksiteId");
        if (worksiteId == null) {
            DispatchInfo dispatch = dispatch(event, payload);
            worksiteId = dispatch == null ? null : dispatch.worksiteId();
        }
        if (worksiteId == null) {
            return 0;
        }
        final UUID siteId = worksiteId;
        // Criterio v4: "Una discrepancia abierta genera el aviso a los responsables de esa obra"
        // Se pasa actorId = null para asegurar que los encargados de la obra reciban el aviso
        Audience audience = audience(null, event.organizationId(), NotificationType.DISCREPANCY_OPENED, siteId,
                () -> recipients.assignedTo(event.organizationId(), siteId, Recipient.SITE_MANAGER), null);
        String worksite = locationName(event, siteId, "la obra");
        String material = text(payload, "materialName") != null ? text(payload, "materialName") : text(payload, "sku");
        String reason = text(payload, "reason") != null ? text(payload, "reason") : text(payload, "text");
        NotificationTemplate.RenderedNotification rendered = NotificationTemplate.discrepancyOpened(worksite,
                material, reason, discrepancyId);
        return notify(event, audience, NotificationType.DISCREPANCY_OPENED, rendered.title(), rendered.body(),
                rendered.link());
    }

    private int orderApproved(EventEnvelope event, JsonNode payload) {
        UUID orderId = uuid(payload, "orderId");
        if (orderId == null && event.aggregateId() != null) {
            orderId = parse(event.aggregateId());
        }
        if (orderId == null) {
            return 0;
        }
        UUID worksiteId = uuid(payload, "worksiteId");
        UUID warehouseId = uuid(payload, "warehouseId");
        final UUID siteId = worksiteId;
        Audience audience = audience(event.actorId(), event.organizationId(), NotificationType.ORDER_APPROVED, siteId,
                () -> {
                    List<Recipient> list = new ArrayList<>();
                    if (siteId != null) {
                        list.addAll(recipients.assignedTo(event.organizationId(), siteId, Recipient.SITE_MANAGER));
                    }
                    if (warehouseId != null) {
                        list.addAll(warehouseManagers(event, warehouseId));
                    }
                    return list;
                }, null);
        String orderCode = text(payload, "orderCode") != null ? text(payload, "orderCode") : text(payload, "orderNumber");
        String worksite = locationName(event, siteId, "la obra");
        NotificationTemplate.RenderedNotification rendered = NotificationTemplate.orderApproved(orderCode, worksite, orderId);
        return notify(event, audience, NotificationType.ORDER_APPROVED, rendered.title(), rendered.body(), rendered.link());
    }

    private int stockReservationRejected(EventEnvelope event, JsonNode payload) {
        UUID orderId = uuid(payload, "orderId");
        if (orderId == null && event.aggregateId() != null) {
            orderId = parse(event.aggregateId());
        }
        if (orderId == null) {
            return 0;
        }
        UUID worksiteId = uuid(payload, "worksiteId");
        final UUID siteId = worksiteId;
        Audience audience = audience(event.actorId(), event.organizationId(), NotificationType.STOCK_RESERVATION_REJECTED,
                siteId, () -> {
                    List<Recipient> list = new ArrayList<>();
                    if (siteId != null) {
                        list.addAll(recipients.assignedTo(event.organizationId(), siteId, Recipient.SITE_MANAGER));
                    }
                    list.addAll(recipients.withRole(event.organizationId(), Recipient.ADMINISTRATOR));
                    return list;
                }, null);
        String orderCode = text(payload, "orderCode") != null ? text(payload, "orderCode") : text(payload, "orderNumber");
        String worksite = locationName(event, siteId, "la obra");
        String reason = text(payload, "reason") != null ? text(payload, "reason") : text(payload, "observation");
        NotificationTemplate.RenderedNotification rendered = NotificationTemplate.stockReservationRejected(orderCode,
                worksite, reason, orderId);
        return notify(event, audience, NotificationType.STOCK_RESERVATION_REJECTED, rendered.title(), rendered.body(),
                rendered.link());
    }

    private int paymentSucceeded(EventEnvelope event, JsonNode payload) {
        Audience audience = audience(event.actorId(), event.organizationId(), NotificationType.PAYMENT_SUCCEEDED, null,
                () -> recipients.withRole(event.organizationId(), Recipient.ADMINISTRATOR), null);
        String plan = text(payload, "plan");
        NotificationTemplate.RenderedNotification rendered = NotificationTemplate.paymentSucceeded(plan,
                event.organizationId());
        return notify(event, audience, NotificationType.PAYMENT_SUCCEEDED, rendered.title(), rendered.body(),
                rendered.link());
    }

    private int paymentFailed(EventEnvelope event, JsonNode payload) {
        Audience audience = audience(event.actorId(), event.organizationId(), NotificationType.PAYMENT_FAILED, null,
                () -> recipients.withRole(event.organizationId(), Recipient.ADMINISTRATOR), null);
        String plan = text(payload, "plan");
        String reason = text(payload, "failureReason") != null ? text(payload, "failureReason") : text(payload, "reason");
        NotificationTemplate.RenderedNotification rendered = NotificationTemplate.paymentFailed(plan, reason,
                event.organizationId());
        return notify(event, audience, NotificationType.PAYMENT_FAILED, rendered.title(), rendered.body(),
                rendered.link());
    }

    private int subscriptionExpired(EventEnvelope event, JsonNode payload) {
        Audience audience = audience(event.actorId(), event.organizationId(), NotificationType.SUBSCRIPTION_EXPIRED, null,
                () -> recipients.withRole(event.organizationId(), Recipient.ADMINISTRATOR), null);
        String plan = text(payload, "plan");
        NotificationTemplate.RenderedNotification rendered = NotificationTemplate.subscriptionExpired(plan,
                event.organizationId());
        return notify(event, audience, NotificationType.SUBSCRIPTION_EXPIRED, rendered.title(), rendered.body(),
                rendered.link());
    }

    private int passwordResetRequested(EventEnvelope event, JsonNode payload) {
        String toEmail = text(payload, "email");
        String resetUrl = text(payload, "resetUrl");
        if (resetUrl == null) {
            String token = text(payload, "token");
            if (token != null) {
                resetUrl = "/reset-password?token=" + token;
            }
        }
        NotificationTemplate.RenderedNotification rendered = NotificationTemplate.passwordReset(resetUrl);
        UUID userId = uuid(payload, "userId");
        if (toEmail != null) {
            try {
                email.send(toEmail, rendered.title(), rendered.body());
            } catch (CallNotPermittedException ex) {
                log.warn("Circuit breaker abierto para SendGrid; correo de recuperación para {} no se envió", toEmail);
            } catch (RuntimeException ex) {
                log.warn("No se pudo enviar el correo de recuperación a {}: {}", toEmail, ex.getMessage());
            }
        }
        if (userId != null) {
            Instant now = clock.instant();
            Notification notification = Notification.create(event.organizationId(), userId,
                    NotificationType.PASSWORD_RESET_REQUESTED, rendered.title(), rendered.body(), rendered.link(), now);
            if (toEmail != null) {
                notification.markSent(now);
            }
            notifications.save(notification);
            return 1;
        }
        return toEmail != null ? 1 : 0;
    }

    private List<Recipient> warehouseManagers(EventEnvelope event, UUID warehouseId) {
        return warehouseId == null ? List.of()
                : recipients.assignedTo(event.organizationId(), warehouseId, Recipient.WAREHOUSE_MANAGER);
    }

    /**
     * Destinatarios del aviso: los configurados para la obra y el tipo (US45) o, si no hay configuración, los de la
     * regla por defecto; más la persona directamente involucrada, si la hay. Sin quien provocó el evento.
     */
    private Audience audience(UUID actorId, UUID organizationId, NotificationType type, UUID worksiteId,
            Supplier<? extends Collection<Recipient>> byDefault, Recipient personal) {
        Audience audience = new Audience(actorId);
        var setting = worksiteId == null ? Optional.<RecipientSetting>empty()
                : settings.find(organizationId, worksiteId, type);
        if (setting.isPresent()) {
            Set<UUID> chosen = Set.copyOf(setting.get().userIds());
            audience.add(recipients.candidates(organizationId, worksiteId).stream()
                    .filter(candidate -> chosen.contains(candidate.userId())).toList());
        } else {
            audience.add(byDefault.get());
        }
        return audience.add(personal);
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
        List<Notification> created = targets.stream().map(recipient -> Notification.create(event.organizationId(),
                recipient.userId(), type, title, body, linkFor(recipient, link), now)).toList();
        notifications.saveAll(created);
        sendEmailsAfterCommit(targets, created, title, body);
        return targets.size();
    }

    /**
     * El detalle del despacho ({@code /dispatches/{id}}) es una pantalla de administración y almacén: al encargado de
     * obra se le lleva al historial de custodia del mismo despacho ({@code /dispatches/{id}/custody}).
     */
    static String linkFor(Recipient recipient, String link) {
        if (Recipient.SITE_MANAGER.equals(recipient.role()) && link.matches("/dispatches/[^/]+")) {
            return link + "/custody";
        }
        return link;
    }

    /** Cada intento de correo queda en {@code status}, {@code attempts} y {@code sent_at} del aviso. */
    private void sendEmailsAfterCommit(List<Recipient> targets, List<Notification> created, String subject,
            String body) {
        Runnable send = () -> {
            for (int i = 0; i < targets.size(); i++) {
                Recipient recipient = targets.get(i);
                if (recipient.email() == null) {
                    continue;
                }
                Notification notification = created.get(i);
                try {
                    email.send(recipient.email(), subject, body);
                    notification.markSent(clock.instant());
                } catch (CallNotPermittedException ex) {
                    log.warn("Circuit breaker abierto para SendGrid; el aviso {} queda en PENDING para reintento", notification.id());
                    // Permanece en PENDING, attempts = 0
                } catch (RuntimeException ex) {
                    log.warn("No se pudo enviar el correo a {}: {}", recipient.userId(), ex.getMessage());
                    notification.markFailed();
                }
                try {
                    notifications.recordDelivery(notification);
                } catch (RuntimeException ex) {
                    log.warn("No se pudo registrar el envío del aviso {}: {}", notification.id(), ex.getMessage());
                }
            }
        };
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
