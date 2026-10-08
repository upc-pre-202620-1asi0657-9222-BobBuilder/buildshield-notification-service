package pe.buildshield.notification.domain.model;

/** Qué evento originó la notificación (US43). */
public enum NotificationType {
    DISPATCH_IN_TRANSIT,
    DISPATCH_CANCELLED,
    TRANSIT_INCIDENT,
    RECEPTION_WITH_DISCREPANCIES,
    DISCREPANCY_RESPONDED,
    DISCREPANCY_ESCALATED,
    DISCREPANCY_RESOLVED,
    DISCREPANCY_ACCEPTED
}
