package pe.buildshield.notification.domain.model;

/**
 * Qué evento originó la notificación (US43), con su nombre en español y a quién avisa por defecto cuando el
 * administrador no configuró destinatarios para la obra (US45).
 */
public enum NotificationType {
    DISPATCH_IN_TRANSIT("Despacho en camino", "Encargados de obra asignados a la obra"),
    DISPATCH_CANCELLED("Despacho anulado", "Encargados de obra asignados a la obra"),
    TRANSIT_INCIDENT("Incidencia en ruta", "Encargados de obra asignados a la obra y administradores"),
    RECEPTION_WITH_DISCREPANCIES("Recepción con discrepancias",
            "Encargados de almacén del almacén de origen y administradores"),
    DISCREPANCY_RESPONDED("Discrepancia respondida", "Solo quien abrió la discrepancia"),
    DISCREPANCY_ESCALATED("Discrepancia escalada", "Administradores"),
    DISCREPANCY_RESOLVED("Discrepancia resuelta",
            "Encargados de almacén del almacén de origen y quien abrió la discrepancia"),
    DISCREPANCY_ACCEPTED("Discrepancia aceptada",
            "Encargados de almacén del almacén de origen y quien abrió la discrepancia");

    private final String label;
    private final String defaultAudience;

    NotificationType(String label, String defaultAudience) {
        this.label = label;
        this.defaultAudience = defaultAudience;
    }

    public String label() {
        return label;
    }

    public String defaultAudience() {
        return defaultAudience;
    }
}
