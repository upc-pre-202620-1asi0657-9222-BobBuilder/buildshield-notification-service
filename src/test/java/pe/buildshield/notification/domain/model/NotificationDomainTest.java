package pe.buildshield.notification.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationDomainTest {

    private static final Instant NOW = Instant.parse("2026-11-03T13:30:00Z");

    @Test
    void a_notification_is_read_once_and_keeps_the_first_reading() {
        UUID user = UUID.randomUUID();
        Notification notification = Notification.create(UUID.randomUUID(), user, NotificationType.TRANSIT_INCIDENT,
                "Incidencia", "Retraso", "/dispatches/1", NOW);

        assertThat(notification.isRead()).isFalse();
        assertThat(notification.belongsTo(user)).isTrue();
        assertThat(notification.belongsTo(UUID.randomUUID())).isFalse();
        notification.markRead(NOW.plusSeconds(10));
        notification.markRead(NOW.plusSeconds(99));
        assertThat(notification.readAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void long_texts_are_cut_to_the_column_size_and_required_fields_are_checked() {
        Notification notification = Notification.create(UUID.randomUUID(), UUID.randomUUID(),
                NotificationType.DISPATCH_IN_TRANSIT, "t".repeat(200), "b".repeat(600), "/x", NOW);

        assertThat(notification.title()).hasSize(Notification.MAX_TITLE).endsWith("…");
        assertThat(notification.body()).hasSize(Notification.MAX_BODY);
        assertThat(notification.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.attempts()).isZero();
        assertThat(notification.sentAt()).isNull();
        assertThatThrownBy(() -> Notification.create(UUID.randomUUID(), null, NotificationType.DISPATCH_IN_TRANSIT,
                "t", "b", "/x", NOW)).isInstanceOf(NullPointerException.class);
        Notification restored = Notification.restore(notification.id(), notification.organizationId(),
                notification.recipientUserId(), notification.type(), "t", "b", "/x", NotificationStatus.SENT, 1, NOW, NOW, NOW);
        assertThat(restored.isRead()).isTrue();
        assertThat(restored.link()).isEqualTo("/x");
        assertThat(restored.createdAt()).isEqualTo(NOW);
        assertThat(restored.status()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void delivery_attempts_are_counted_and_the_last_outcome_wins() {
        Notification notification = Notification.create(UUID.randomUUID(), UUID.randomUUID(),
                NotificationType.TRANSIT_INCIDENT, "t", "b", "/x", NOW);

        notification.markFailed();
        assertThat(notification.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.attempts()).isEqualTo(1);
        assertThat(notification.sentAt()).isNull();

        notification.markSent(NOW.plusSeconds(5));
        assertThat(notification.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.attempts()).isEqualTo(2);
        assertThat(notification.sentAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void audience_skips_the_actor_inactive_users_and_repeated_recipients() {
        UUID actor = UUID.randomUUID();
        Recipient ana = new Recipient(UUID.randomUUID(), "Ana", "ana@obra.pe", Recipient.ADMINISTRATOR, true);
        Recipient off = new Recipient(UUID.randomUUID(), "Off", null, Recipient.ADMINISTRATOR, false);
        Recipient self = new Recipient(actor, "Yo", null, Recipient.SITE_MANAGER, true);

        Audience audience = new Audience(actor).add(List.of(ana, off, self)).add(ana).add((Recipient) null);

        assertThat(audience.recipients()).containsExactly(ana);
        assertThat(new Audience(null).isEmpty()).isTrue();
    }

    @Test
    void dispatch_reference_is_the_manifest_or_a_short_id() {
        UUID id = UUID.fromString("6f7a8b9c-0d1e-4f2a-8b3c-4d5e6f7a8b9c");
        assertThat(new DispatchInfo(id, "MAN-1", null, null).reference()).isEqualTo("MAN-1");
        assertThat(new DispatchInfo(id, " ", null, null).reference()).isEqualTo("6f7a8b9c");
        assertThat(new DispatchInfo(id, null, null, null).reference()).isEqualTo("6f7a8b9c");
    }
}
