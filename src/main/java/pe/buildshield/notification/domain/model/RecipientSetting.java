package pe.buildshield.notification.domain.model;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Destinatarios que el administrador eligió para un tipo de aviso en una obra (US45). Reemplazan a los destinatarios
 * por rol de la regla por defecto; una lista vacía significa que nadie recibe ese aviso por rol en esa obra. Quien
 * abrió una discrepancia sigue recibiendo los avisos de su caso, y nunca se avisa a quien provocó el evento.
 */
public record RecipientSetting(UUID worksiteId, NotificationType type, List<UUID> userIds, UUID updatedBy,
        Instant updatedAt) {

    /** Máximo de destinatarios por obra y tipo: una lista más larga es casi seguro un error de la interfaz. */
    public static final int MAX_RECIPIENTS = 50;

    public RecipientSetting {
        Objects.requireNonNull(worksiteId, "worksiteId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(updatedBy, "updatedBy");
        Objects.requireNonNull(updatedAt, "updatedAt");
        // Sin repetidos, en el orden elegido.
        userIds = List.copyOf(new LinkedHashSet<>(Objects.requireNonNull(userIds, "userIds")));
    }
}
