package pe.buildshield.notification.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Aviso en la bandeja de un usuario. Cambia al marcarse como leído y al registrar el envío por correo. */
public class Notification {

    public static final int MAX_TITLE = 150;
    public static final int MAX_BODY = 500;

    private final UUID id;
    private final UUID organizationId;
    private final UUID recipientUserId;
    private final NotificationType type;
    private final String title;
    private final String body;
    private final String link;
    private NotificationStatus status;
    private int attempts;
    private final Instant createdAt;
    private Instant sentAt;
    private Instant readAt;

    private Notification(UUID id, UUID organizationId, UUID recipientUserId, NotificationType type, String title,
            String body, String link, NotificationStatus status, int attempts, Instant createdAt, Instant sentAt,
            Instant readAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.recipientUserId = Objects.requireNonNull(recipientUserId, "recipientUserId");
        this.type = Objects.requireNonNull(type, "type");
        this.title = truncate(Objects.requireNonNull(title, "title"), MAX_TITLE);
        this.body = truncate(Objects.requireNonNull(body, "body"), MAX_BODY);
        this.link = Objects.requireNonNull(link, "link");
        this.status = Objects.requireNonNull(status, "status");
        this.attempts = attempts;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.sentAt = sentAt;
        this.readAt = readAt;
    }

    public static Notification create(UUID organizationId, UUID recipientUserId, NotificationType type, String title,
            String body, String link, Instant now) {
        return new Notification(UUID.randomUUID(), organizationId, recipientUserId, type, title, body, link,
                NotificationStatus.PENDING, 0, now, null, null);
    }

    public static Notification restore(UUID id, UUID organizationId, UUID recipientUserId, NotificationType type,
            String title, String body, String link, NotificationStatus status, int attempts, Instant createdAt,
            Instant sentAt, Instant readAt) {
        return new Notification(id, organizationId, recipientUserId, type, title, body, link, status, attempts,
                createdAt, sentAt, readAt);
    }

    /** Marca como leída; si ya lo estaba, conserva la primera lectura. */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    /** Registra un envío por correo exitoso. */
    public void markSent(Instant now) {
        attempts++;
        status = NotificationStatus.SENT;
        sentAt = now;
    }

    /** Registra un envío por correo fallido. */
    public void markFailed() {
        attempts++;
        status = NotificationStatus.FAILED;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public boolean belongsTo(UUID userId) {
        return recipientUserId.equals(userId);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    public UUID id() {
        return id;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public UUID recipientUserId() {
        return recipientUserId;
    }

    public NotificationType type() {
        return type;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    public String link() {
        return link;
    }

    public NotificationStatus status() {
        return status;
    }

    public int attempts() {
        return attempts;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant sentAt() {
        return sentAt;
    }

    public Instant readAt() {
        return readAt;
    }
}
