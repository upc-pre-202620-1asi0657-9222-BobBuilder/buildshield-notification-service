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

        notification.markPending();
        assertThat(notification.status()).isEqualTo(NotificationStatus.PENDING);
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

    @Test
    void notification_template_renders_all_notification_types_correctly() {
        UUID id = UUID.randomUUID();

        var inTransit = NotificationTemplate.dispatchInTransit("Torre Norte", "Almacén Central", "MAN-1", id);
        assertThat(inTransit.title()).isEqualTo("Despacho en camino a Torre Norte");
        assertThat(inTransit.body()).contains("MAN-1").contains("Almacén Central").contains("Torre Norte");
        assertThat(inTransit.link()).isEqualTo("/reception/" + id);

        var cancelled = NotificationTemplate.dispatchCancelled("Torre Norte", "MAN-1", "Falta material", id);
        assertThat(cancelled.title()).isEqualTo("Despacho anulado");
        assertThat(cancelled.body()).contains("fue anulado: Falta material");
        assertThat(cancelled.link()).isEqualTo("/dispatches/" + id);

        var incident = NotificationTemplate.transitIncident("Retraso", "MAN-1", "Torre Norte", "Tráfico pesado", id);
        assertThat(incident.title()).isEqualTo("Incidencia en ruta: Retraso");
        assertThat(incident.body()).contains("MAN-1 hacia Torre Norte: Tráfico pesado");

        var reception = NotificationTemplate.receptionWithDiscrepancies("MAN-1", "Torre Norte", 2, id);
        assertThat(reception.title()).isEqualTo("Recepción con discrepancias");
        assertThat(reception.body()).contains("2 materiales fuera de la tolerancia");

        var discResponded = NotificationTemplate.discrepancy(NotificationType.DISCREPANCY_RESPONDED,
                "Cemento Portland", "MAN-1", "Respuesta", id);
        assertThat(discResponded.title()).isEqualTo("Discrepancia respondida por el almacén");
        assertThat(discResponded.body()).isEqualTo("Cemento Portland del despacho MAN-1: Respuesta");

        var discOpened = NotificationTemplate.discrepancyOpened("Torre Norte", "Arena gruesa", "Bolsas rotas", id);
        assertThat(discOpened.title()).isEqualTo("Discrepancia abierta en Torre Norte");
        assertThat(discOpened.body()).contains("Arena gruesa").contains("Bolsas rotas");

        var order = NotificationTemplate.orderApproved("ORD-101", "Torre Norte", id);
        assertThat(order.title()).isEqualTo("Pedido aprobado");
        assertThat(order.body()).contains("ORD-101").contains("Torre Norte");
        assertThat(order.link()).isEqualTo("/orders/" + id);

        var stockRej = NotificationTemplate.stockReservationRejected("ORD-101", "Torre Norte", "Sin stock", id);
        assertThat(stockRej.title()).isEqualTo("Reserva de stock rechazada");
        assertThat(stockRej.body()).contains("ORD-101").contains("Sin stock");

        var payOk = NotificationTemplate.paymentSucceeded("Pro", id);
        assertThat(payOk.title()).isEqualTo("Pago de suscripción exitoso");
        assertThat(payOk.body()).contains("Pro");

        var payFail = NotificationTemplate.paymentFailed("Pro", "Fondos insuficientes", id);
        assertThat(payFail.title()).isEqualTo("Fallo en el pago de la suscripción");
        assertThat(payFail.body()).contains("Fondos insuficientes");

        var subExp = NotificationTemplate.subscriptionExpired("Pro", id);
        assertThat(subExp.title()).isEqualTo("Suscripción vencida");
        assertThat(subExp.body()).contains("Pro");

        var reset = NotificationTemplate.passwordReset("https://app.buildshield.pe/reset?token=xyz");
        assertThat(reset.title()).isEqualTo("Recuperación de contraseña");
        assertThat(reset.link()).isEqualTo("https://app.buildshield.pe/reset?token=xyz");
    }

    @Test
    void default_templates_exist_for_all_notification_types() {
        for (NotificationType type : NotificationType.values()) {
            NotificationTemplate template = NotificationTemplate.defaultTemplate(type);
            assertThat(template.type()).isEqualTo(type);
            assertThat(template.titleTemplate()).isNotBlank();
            assertThat(template.bodyTemplate()).isNotBlank();
            assertThat(template.linkTemplate()).isNotBlank();
            assertThat(template.id()).isNotNull();
        }
    }
}
