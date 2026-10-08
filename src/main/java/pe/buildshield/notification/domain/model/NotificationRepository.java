package pe.buildshield.notification.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Bandeja de notificaciones. Las lecturas se filtran por la organización del contexto. */
public interface NotificationRepository {

    void saveAll(List<Notification> notifications);

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    NotificationPage findByRecipient(UUID recipientUserId, boolean unreadOnly, int page, int size);

    long countUnread(UUID recipientUserId);

    int markAllRead(UUID recipientUserId, Instant now);
}
