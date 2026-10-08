package pe.buildshield.notification.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import pe.buildshield.notification.domain.model.Notification;
import pe.buildshield.notification.domain.model.NotificationPage;
import pe.buildshield.notification.domain.model.NotificationRepository;
import pe.buildshield.notification.shared.error.ResourceNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** El filtro multiempresa limita toda lectura a la organización del contexto. */
@Repository
class JpaNotificationRepository implements NotificationRepository {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final SpringDataNotificationRepository jpa;

    JpaNotificationRepository(SpringDataNotificationRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void saveAll(List<Notification> notifications) {
        jpa.saveAllAndFlush(notifications.stream().map(NotificationJpaEntity::new).toList());
    }

    @Override
    public Notification save(Notification notification) {
        NotificationJpaEntity entity = jpa.findById(notification.id()).orElseThrow(() ->
                new ResourceNotFoundException("La notificación " + notification.id() + " no existe"));
        entity.markRead(notification.readAt());
        return jpa.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jpa.findById(id).map(NotificationJpaEntity::toDomain);
    }

    @Override
    public NotificationPage findByRecipient(UUID recipientUserId, boolean unreadOnly, int page, int size) {
        PageRequest request = PageRequest.of(page, size, NEWEST_FIRST);
        Page<NotificationJpaEntity> found = unreadOnly
                ? jpa.findByRecipientUserIdAndReadAtIsNull(recipientUserId, request)
                : jpa.findByRecipientUserId(recipientUserId, request);
        return new NotificationPage(found.getContent().stream().map(NotificationJpaEntity::toDomain).toList(), page,
                size, found.getTotalElements());
    }

    @Override
    public long countUnread(UUID recipientUserId) {
        return jpa.countByRecipientUserIdAndReadAtIsNull(recipientUserId);
    }

    @Override
    public int markAllRead(UUID recipientUserId, Instant now) {
        return jpa.markAllRead(recipientUserId, now);
    }
}
