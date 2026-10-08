package pe.buildshield.notification.domain.model;

import java.util.UUID;

/** Usuario que puede recibir avisos, según la copia local de iam. */
public record Recipient(UUID userId, String fullName, String email, String role, boolean active) {

    public static final String ADMINISTRATOR = "ADMINISTRATOR";
    public static final String WAREHOUSE_MANAGER = "WAREHOUSE_MANAGER";
    public static final String SITE_MANAGER = "SITE_MANAGER";
}
