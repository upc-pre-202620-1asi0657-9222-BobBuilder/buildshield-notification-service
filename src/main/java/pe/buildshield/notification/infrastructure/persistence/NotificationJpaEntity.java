package pe.buildshield.notification.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pe.buildshield.notification.domain.model.Notification;
import pe.buildshield.notification.domain.model.NotificationType;
import pe.buildshield.notification.shared.persistence.OrganizationScopedEntity;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationJpaEntity extends OrganizationScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40, updatable = false)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 150, updatable = false)
    private String title;

    @Column(name = "body", nullable = false, length = 500, updatable = false)
    private String body;

    @Column(name = "link", nullable = false, length = 200, updatable = false)
    private String link;

    @Column(name = "source_event_id", updatable = false)
    private UUID sourceEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected NotificationJpaEntity() {
    }

    NotificationJpaEntity(Notification notification) {
        id = notification.id();
        recipientUserId = notification.recipientUserId();
        type = notification.type();
        title = notification.title();
        body = notification.body();
        link = notification.link();
        sourceEventId = notification.sourceEventId();
        createdAt = notification.createdAt();
        readAt = notification.readAt();
    }

    void markRead(Instant when) {
        readAt = when;
    }

    UUID getId() {
        return id;
    }

    Notification toDomain() {
        return Notification.restore(id, getOrganizationId(), recipientUserId, type, title, body, link, sourceEventId,
                createdAt, readAt);
    }
}
