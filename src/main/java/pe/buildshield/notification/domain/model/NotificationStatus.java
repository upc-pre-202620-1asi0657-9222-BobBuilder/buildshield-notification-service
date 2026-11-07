package pe.buildshield.notification.domain.model;

/** Estado del envío del aviso por correo (SendGrid). */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED
}
