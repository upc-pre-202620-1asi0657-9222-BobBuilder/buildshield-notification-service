package pe.buildshield.notification.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Bandeja de notificaciones. Las lecturas se filtran por la organización del contexto. */
public interface NotificationRepository {

    void saveAll(List<Notification> notifications);

    Notification save(Notification notification);

    /** Guarda estado, intentos y fecha de envío; corre en su propia transacción porque se llama después del commit. */
    void recordDelivery(Notification notification);

    Optional<Notification> findById(UUID id);

    NotificationPage findByRecipient(UUID recipientUserId, boolean unreadOnly, int page, int size);

    long countUnread(UUID recipientUserId);

    int markAllRead(UUID recipientUserId, Instant now);

    /** Obtiene las notificaciones en estado PENDING de la organización para reintento de envío. */
    List<Notification> findPending();
}
