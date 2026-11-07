package pe.buildshield.notification.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Agregado del dominio (C4 pág. 29) que encapsula las plantillas y el formateo de
 * título, cuerpo y enlace para cada tipo de notificación.
 * Es dominio puro: sin dependencias de Spring, JPA, Hibernate ni Kafka (RNF18).
 */
public class NotificationTemplate {

    public record RenderedNotification(String title, String body, String link) {
        public RenderedNotification {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(link, "link");
        }
    }

    private final UUID id;
    private final NotificationType type;
    private final String titleTemplate;
    private final String bodyTemplate;
    private final String linkTemplate;

    public NotificationTemplate(UUID id, NotificationType type, String titleTemplate, String bodyTemplate,
            String linkTemplate) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.titleTemplate = Objects.requireNonNull(titleTemplate, "titleTemplate");
        this.bodyTemplate = Objects.requireNonNull(bodyTemplate, "bodyTemplate");
        this.linkTemplate = Objects.requireNonNull(linkTemplate, "linkTemplate");
    }

    public NotificationTemplate(NotificationType type, String titleTemplate, String bodyTemplate,
            String linkTemplate) {
        this(UUID.randomUUID(), type, titleTemplate, bodyTemplate, linkTemplate);
    }

    public UUID id() {
        return id;
    }

    public NotificationType type() {
        return type;
    }

    public String titleTemplate() {
        return titleTemplate;
    }

    public String bodyTemplate() {
        return bodyTemplate;
    }

    public String linkTemplate() {
        return linkTemplate;
    }

    public static NotificationTemplate defaultTemplate(NotificationType type) {
        return switch (type) {
            case DISPATCH_IN_TRANSIT -> new NotificationTemplate(type,
                    "Despacho en camino a {worksite}",
                    "El despacho {reference} salió de {warehouse} hacia {worksite}. Prepárate para recibirlo.",
                    "/reception/{dispatchId}");
            case DISPATCH_CANCELLED -> new NotificationTemplate(type,
                    "Despacho anulado",
                    "El despacho {reference} hacia {worksite} fue anulado{reason}",
                    "/dispatches/{dispatchId}");
            case TRANSIT_INCIDENT -> new NotificationTemplate(type,
                    "Incidencia en ruta: {incidentType}",
                    "Despacho {reference}{worksiteSuffix}{observationSuffix}",
                    "/dispatches/{dispatchId}");
            case RECEPTION_WITH_DISCREPANCIES -> new NotificationTemplate(type,
                    "Recepción con discrepancias",
                    "La recepción del despacho {reference} en {worksite} tiene {materials} de la tolerancia.",
                    "/dispatches/{dispatchId}");
            case DISCREPANCY_RESPONDED -> new NotificationTemplate(type,
                    "Discrepancia respondida por el almacén",
                    "{material}{manifestSuffix}{detailSuffix}",
                    "/discrepancies/{discrepancyId}");
            case DISCREPANCY_ESCALATED -> new NotificationTemplate(type,
                    "Discrepancia escalada: requiere tu decisión",
                    "{material}{manifestSuffix}{detailSuffix}",
                    "/discrepancies/{discrepancyId}");
            case DISCREPANCY_RESOLVED -> new NotificationTemplate(type,
                    "Discrepancia resuelta",
                    "{material}{manifestSuffix}{detailSuffix}",
                    "/discrepancies/{discrepancyId}");
            case DISCREPANCY_ACCEPTED -> new NotificationTemplate(type,
                    "Discrepancia aceptada por la obra",
                    "{material}{manifestSuffix}{detailSuffix}",
                    "/discrepancies/{discrepancyId}");
            case DISCREPANCY_OPENED -> new NotificationTemplate(type,
                    "Discrepancia abierta en {worksite}",
                    "Se abrió una discrepancia para {material} en {worksite}{detailSuffix}",
                    "/discrepancies/{discrepancyId}");
            case ORDER_APPROVED -> new NotificationTemplate(type,
                    "Pedido aprobado",
                    "El pedido {orderCode} para {worksite} ha sido aprobado.",
                    "/orders/{orderId}");
            case STOCK_RESERVATION_REJECTED -> new NotificationTemplate(type,
                    "Reserva de stock rechazada",
                    "La reserva de stock para el pedido {orderCode} en {worksite} fue rechazada{reasonSuffix}",
                    "/orders/{orderId}");
            case PAYMENT_SUCCEEDED -> new NotificationTemplate(type,
                    "Pago de suscripción exitoso",
                    "El pago de la suscripción al plan {plan} fue procesado correctamente.",
                    "/subscription");
            case PAYMENT_FAILED -> new NotificationTemplate(type,
                    "Fallo en el pago de la suscripción",
                    "No se pudo procesar el pago de la suscripción al plan {plan}{reasonSuffix}",
                    "/subscription");
            case SUBSCRIPTION_EXPIRED -> new NotificationTemplate(type,
                    "Suscripción vencida",
                    "Tu suscripción al plan {plan} ha vencido. Renueva para continuar disfrutando del servicio.",
                    "/subscription");
            case PASSWORD_RESET_REQUESTED -> new NotificationTemplate(type,
                    "Recuperación de contraseña",
                    "Has solicitado restablecer tu contraseña. Usa el siguiente enlace para continuar: {resetUrl}",
                    "{resetUrl}");
        };
    }

    public static RenderedNotification dispatchInTransit(String worksite, String warehouse, String reference,
            UUID dispatchId) {
        String title = "Despacho en camino a " + worksite;
        String body = "El despacho " + reference + " salió de " + warehouse + " hacia " + worksite
                + ". Prepárate para recibirlo.";
        String link = "/reception/" + dispatchId;
        return new RenderedNotification(title, body, link);
    }

    public static RenderedNotification dispatchCancelled(String worksite, String reference, String reason,
            UUID dispatchId) {
        String title = "Despacho anulado";
        String body = "El despacho " + reference + " hacia " + worksite + " fue anulado"
                + (reason == null ? "." : ": " + reason);
        String link = "/dispatches/" + dispatchId;
        return new RenderedNotification(title, body, link);
    }

    public static RenderedNotification transitIncident(String incidentType, String reference, String worksite,
            String observation, UUID dispatchId) {
        String title = "Incidencia en ruta: " + incidentType;
        String body = "Despacho " + reference + (worksite == null ? "" : " hacia " + worksite)
                + (observation == null ? "." : ": " + observation);
        String link = "/dispatches/" + dispatchId;
        return new RenderedNotification(title, body, link);
    }

    public static RenderedNotification receptionWithDiscrepancies(String reference, String worksite,
            int outOfTolerance, UUID dispatchId) {
        String materials = outOfTolerance == 1 ? "1 material fuera" : outOfTolerance + " materiales fuera";
        String title = "Recepción con discrepancias";
        String body = "La recepción del despacho " + reference + " en " + worksite + " tiene " + materials
                + " de la tolerancia.";
        String link = "/dispatches/" + dispatchId;
        return new RenderedNotification(title, body, link);
    }

    public static RenderedNotification discrepancy(NotificationType type, String material, String manifest,
            String detail, UUID discrepancyId) {
        String title = switch (type) {
            case DISCREPANCY_RESPONDED -> "Discrepancia respondida por el almacén";
            case DISCREPANCY_ESCALATED -> "Discrepancia escalada: requiere tu decisión";
            case DISCREPANCY_RESOLVED -> "Discrepancia resuelta";
            default -> "Discrepancia aceptada por la obra";
        };
        String mat = material == null ? "Material" : material;
        String body = mat + (manifest == null ? "" : " del despacho " + manifest)
                + (detail == null ? "." : ": " + detail);
        String link = "/discrepancies/" + discrepancyId;
        return new RenderedNotification(title, body, link);
    }

    public static RenderedNotification discrepancyOpened(String worksite, String material, String reason,
            UUID discrepancyId) {
        String title = "Discrepancia abierta en " + worksite;
        String mat = material == null ? "material" : material;
        String body = "Se abrió una discrepancia para " + mat + " en " + worksite
                + (reason == null ? "." : ": " + reason);
        String link = "/discrepancies/" + discrepancyId;
        return new RenderedNotification(title, body, link);
    }

    public static RenderedNotification orderApproved(String orderCode, String worksite, UUID orderId) {
        String code = orderCode == null ? "solicitado" : orderCode;
        String title = "Pedido aprobado";
        String body = "El pedido " + code + " para " + worksite + " ha sido aprobado.";
        String link = "/orders/" + orderId;
        return new RenderedNotification(title, body, link);
    }

    public static RenderedNotification stockReservationRejected(String orderCode, String worksite, String reason,
            UUID orderId) {
        String code = orderCode == null ? "solicitado" : orderCode;
        String title = "Reserva de stock rechazada";
        String body = "La reserva de stock para el pedido " + code + " en " + worksite + " fue rechazada"
                + (reason == null ? "." : ": " + reason);
        String link = "/orders/" + orderId;
        return new RenderedNotification(title, body, link);
    }

    public static RenderedNotification paymentSucceeded(String plan, UUID organizationId) {
        String p = plan == null ? "actual" : plan;
        String title = "Pago de suscripción exitoso";
        String body = "El pago de la suscripción al plan " + p + " fue procesado correctamente.";
        String link = "/subscription";
        return new RenderedNotification(title, body, link);
    }

    public static RenderedNotification paymentFailed(String plan, String reason, UUID organizationId) {
        String p = plan == null ? "actual" : plan;
        String title = "Fallo en el pago de la suscripción";
        String body = "No se pudo procesar el pago de la suscripción al plan " + p
                + (reason == null ? "." : ": " + reason);
        String link = "/subscription";
        return new RenderedNotification(title, body, link);
    }

    public static RenderedNotification subscriptionExpired(String plan, UUID organizationId) {
        String p = plan == null ? "actual" : plan;
        String title = "Suscripción vencida";
        String body = "Tu suscripción al plan " + p + " ha vencido. Renueva para continuar disfrutando del servicio.";
        String link = "/subscription";
        return new RenderedNotification(title, body, link);
    }

    public static RenderedNotification passwordReset(String resetUrl) {
        String url = resetUrl == null ? "/reset-password" : resetUrl;
        String title = "Recuperación de contraseña";
        String body = "Has solicitado restablecer tu contraseña. Usa el siguiente enlace para continuar: " + url;
        String link = url;
        return new RenderedNotification(title, body, link);
    }
}
