package pe.buildshield.notification.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.buildshield.notification.domain.model.Notification;
import pe.buildshield.notification.domain.model.NotificationStatus;
import pe.buildshield.notification.domain.model.NotificationType;
import pe.buildshield.notification.shared.persistence.OrganizationScopedEntity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationJpaEntity extends OrganizationScopedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30, updatable = false)
    private NotificationType type;

    /** Datos del mensaje: {@code title}, {@code body} y {@code link}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, String> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private NotificationStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected NotificationJpaEntity() {
    }

    NotificationJpaEntity(Notification notification) {
        id = notification.id();
        userId = notification.recipientUserId();
        type = notification.type();
        payload = Map.of("title", notification.title(), "body", notification.body(), "link", notification.link());
        status = notification.status();
        attempts = notification.attempts();
        createdAt = notification.createdAt();
        sentAt = notification.sentAt();
        readAt = notification.readAt();
    }

    void markRead(Instant when) {
        readAt = when;
    }

    UUID getId() {
        return id;
    }

    Notification toDomain() {
        return Notification.restore(id, getOrganizationId(), userId, type, payload.get("title"), payload.get("body"),
                payload.get("link"), status, attempts, createdAt, sentAt, readAt);
    }
}
