package pe.buildshield.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.buildshield.notification.domain.model.Notification;
import pe.buildshield.notification.domain.model.NotificationStatus;
import pe.buildshield.notification.domain.model.NotificationType;
import pe.buildshield.notification.shared.error.ResourceNotFoundException;
import pe.buildshield.notification.shared.messaging.EventEnvelope;
import pe.buildshield.notification.shared.tenant.TenantContext;
import pe.buildshield.notification.shared.tenant.TenantInfo;
import pe.buildshield.notification.support.MutableClock;
import pe.buildshield.notification.support.TestEvents;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationApplicationTest {

    private static final Instant NOW = Instant.parse("2026-11-03T13:30:00Z");

    private final ObjectMapper json = new ObjectMapper();
    private final UUID organization = UUID.randomUUID();
    private final TestEvents events = new TestEvents(json, organization);
    private final InMemoryNotifications store = new InMemoryNotifications();
    private final List<String> emails = new ArrayList<>();
    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryRecipientSettings settings = new InMemoryRecipientSettings();
    private final NotificationCommandService commands = new NotificationCommandService(store, store, store,
            settings, (to, subject, body) -> emails.add(to + "|" + subject), clock);
    private final NotificationQueryService queries = new NotificationQueryService(store);

    private final UUID ana = UUID.randomUUID();
    private final UUID rosa = UUID.randomUUID();
    private final UUID luis = UUID.randomUUID();
    private final UUID pedro = UUID.randomUUID();
    private final UUID torreNorte = UUID.randomUUID();
    private final UUID otraObra = UUID.randomUUID();
    private final UUID almacen = UUID.randomUUID();
    private final UUID dispatch = UUID.randomUUID();

    @BeforeEach
    void organizationWithStaff() throws Exception {
        handle(events.user("UserCreated", ana, "Ana Torres", "ADMINISTRATOR", true));
        handle(events.user("UserCreated", rosa, "Rosa Quispe", "WAREHOUSE_MANAGER", true));
        handle(events.user("UserCreated", luis, "Luis Ramos", "SITE_MANAGER", true));
        handle(events.user("UserCreated", pedro, "Pedro Soto", "SITE_MANAGER", true));
        handle(events.location(torreNorte, "WORKSITE", "Torre Norte"));
        handle(events.location(almacen, "WAREHOUSE", "Almacén Central"));
        handle(events.assigned(UUID.randomUUID(), luis, torreNorte, "WORKSITE"));
        handle(events.assigned(UUID.randomUUID(), pedro, otraObra, "WORKSITE"));
        handle(events.assigned(UUID.randomUUID(), rosa, almacen, "WAREHOUSE"));
        handle(events.dispatchPrepared(dispatch, "MAN-0001", almacen, torreNorte, rosa));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void dispatch_in_transit_notifies_only_the_site_managers_of_the_destination() throws Exception {
        int created = handle(events.dispatchDeparted(dispatch, almacen, torreNorte, rosa));

        assertThat(created).isEqualTo(1);
        Notification sent = store.notifications.get(0);
        assertThat(sent.recipientUserId()).isEqualTo(luis);
        assertThat(sent.type()).isEqualTo(NotificationType.DISPATCH_IN_TRANSIT);
        assertThat(sent.title()).isEqualTo("Despacho en camino a Torre Norte");
        assertThat(sent.body()).contains("MAN-0001").contains("Almacén Central");
        assertThat(sent.link()).isEqualTo("/reception/" + dispatch);
        assertThat(emails).containsExactly("luis.ramos@obra.pe|Despacho en camino a Torre Norte");
        assertThat(sent.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(sent.attempts()).isEqualTo(1);
        assertThat(sent.sentAt()).isEqualTo(NOW);
    }

    @Test
    void a_failed_email_leaves_the_notification_failed_with_one_attempt() throws Exception {
        NotificationCommandService failing = new NotificationCommandService(store, store, store, settings,
                (to, subject, body) -> {
                    throw new IllegalStateException("SendGrid caído");
                }, clock);

        handle(failing, events.dispatchDeparted(dispatch, almacen, torreNorte, rosa));

        Notification sent = store.notifications.get(0);
        assertThat(sent.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(sent.attempts()).isEqualTo(1);
        assertThat(sent.sentAt()).isNull();
    }

    @Test
    void circuit_breaker_open_leaves_notification_pending_and_retry_sends_it() throws Exception {
        CircuitBreaker cb = CircuitBreaker.ofDefaults("test-sendgrid");
        cb.transitionToOpenState();

        NotificationCommandService cbService = new NotificationCommandService(store, store, store, settings,
                (to, subject, body) -> {
                    if (cb.getState() == CircuitBreaker.State.OPEN) {
                        throw CallNotPermittedException.createCallNotPermittedException(cb);
                    }
                    emails.add(to + "|" + subject);
                }, clock);

        handle(cbService, events.dispatchDeparted(dispatch, almacen, torreNorte, rosa));

        assertThat(store.notifications).hasSize(1);
        Notification pending = store.notifications.get(0);
        assertThat(pending.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(pending.attempts()).isZero();
        assertThat(pending.sentAt()).isNull();
        assertThat(emails).isEmpty();

        // Tras recuperación de SendGrid se reintentan las pendientes
        cb.transitionToClosedState();
        int retried = TenantContext.callAs(new TenantInfo(organization, TenantContext.SYSTEM_USER_ID, "SYSTEM"),
                cbService::retryPending);

        assertThat(retried).isEqualTo(1);
        assertThat(pending.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(pending.attempts()).isEqualTo(1);
        assertThat(pending.sentAt()).isEqualTo(NOW);
        assertThat(emails).containsExactly("luis.ramos@obra.pe|Despacho en camino a Torre Norte");
    }

    @Test
    void discrepancy_opened_notifies_site_managers_of_the_worksite() throws Exception {
        UUID discrepancy = UUID.randomUUID();
        String discrepancyOpened = events.envelope("DiscrepancyOpened", "DISCREPANCY", discrepancy, rosa,
                TestEvents.map("discrepancyId", discrepancy, "worksiteId", torreNorte, "warehouseId", almacen,
                        "materialName", "Cemento Portland", "sku", "CEM-001", "manifestCode", "MAN-0001",
                        "openedBy", rosa, "reason", "Bolsas humedecidas"));

        int created = handle(discrepancyOpened);

        assertThat(created).isEqualTo(1);
        Notification notif = store.notifications.get(0);
        assertThat(notif.recipientUserId()).isEqualTo(luis);
        assertThat(notif.type()).isEqualTo(NotificationType.DISCREPANCY_OPENED);
        assertThat(notif.title()).isEqualTo("Discrepancia abierta en Torre Norte");
        assertThat(notif.body()).contains("Cemento Portland").contains("Bolsas humedecidas");
        assertThat(notif.link()).isEqualTo("/discrepancies/" + discrepancy);
    }

    @Test
    void order_approved_notifies_site_and_warehouse_managers() throws Exception {
        UUID orderId = UUID.randomUUID();
        String approved = events.envelope("OrderApproved", "ORDER", orderId, ana,
                TestEvents.map("orderId", orderId, "orderCode", "PED-0042", "worksiteId", torreNorte,
                        "warehouseId", almacen));

        int created = handle(approved);

        assertThat(created).isEqualTo(2);
        assertThat(store.notifications).extracting(Notification::recipientUserId).containsExactly(luis, rosa);
        assertThat(store.notifications.get(0).title()).isEqualTo("Pedido aprobado");
        assertThat(store.notifications.get(0).body()).contains("PED-0042").contains("Torre Norte");
        assertThat(store.notifications.get(0).link()).isEqualTo("/orders/" + orderId);
    }

    @Test
    void stock_reservation_rejected_notifies_site_managers_and_administrators() throws Exception {
        UUID orderId = UUID.randomUUID();
        String rejected = events.envelope("StockReservationRejected", "ORDER", orderId, rosa,
                TestEvents.map("orderId", orderId, "orderCode", "PED-0042", "worksiteId", torreNorte,
                        "reason", "Sin stock suficiente de arena fina"));

        int created = handle(rejected);

        assertThat(created).isEqualTo(2);
        assertThat(store.notifications).extracting(Notification::recipientUserId).containsExactly(luis, ana);
        assertThat(store.notifications.get(0).title()).isEqualTo("Reserva de stock rechazada");
        assertThat(store.notifications.get(0).body()).contains("PED-0042").contains("Sin stock suficiente de arena fina");
        assertThat(store.notifications.get(0).link()).isEqualTo("/orders/" + orderId);
    }

    @Test
    void subscription_events_notify_administrators() throws Exception {
        UUID subId = UUID.randomUUID();

        String paid = events.envelope("PaymentSucceeded", "PAYMENT", subId, null,
                TestEvents.map("plan", "Pro", "amount", 199.00));
        assertThat(handle(paid)).isEqualTo(1);
        assertThat(store.notifications.get(0).recipientUserId()).isEqualTo(ana);
        assertThat(store.notifications.get(0).title()).isEqualTo("Pago de suscripción exitoso");
        assertThat(store.notifications.get(0).body()).contains("Pro");
        assertThat(store.notifications.get(0).link()).isEqualTo("/subscription");

        String failed = events.envelope("PaymentFailed", "PAYMENT", subId, null,
                TestEvents.map("plan", "Pro", "failureReason", "Tarjeta vencida"));
        assertThat(handle(failed)).isEqualTo(1);
        assertThat(store.notifications.get(1).recipientUserId()).isEqualTo(ana);
        assertThat(store.notifications.get(1).title()).isEqualTo("Fallo en el pago de la suscripción");
        assertThat(store.notifications.get(1).body()).contains("Tarjeta vencida");

        String expired = events.envelope("SubscriptionExpired", "SUBSCRIPTION", subId, null,
                TestEvents.map("plan", "Pro"));
        assertThat(handle(expired)).isEqualTo(1);
        assertThat(store.notifications.get(2).recipientUserId()).isEqualTo(ana);
        assertThat(store.notifications.get(2).title()).isEqualTo("Suscripción vencida");
        assertThat(store.notifications.get(2).body()).contains("Pro");
    }

    @Test
    void password_reset_requested_sends_email_and_does_not_block_if_sendgrid_fails() throws Exception {
        emails.clear();
        String resetEvent = events.envelope("PasswordResetRequested", "USER", luis, null,
                TestEvents.map("userId", luis, "email", "luis.ramos@obra.pe",
                        "resetUrl", "https://app.buildshield.pe/reset?token=abc"));

        int created = handle(resetEvent);
        assertThat(created).isEqualTo(1);
        assertThat(emails).containsExactly("luis.ramos@obra.pe|Recuperación de contraseña");

        // Si SendGrid falla, no se bloquea la operación
        NotificationCommandService failing = new NotificationCommandService(store, store, store, settings,
                (to, subject, body) -> {
                    throw new IllegalStateException("SendGrid error");
                }, clock);
        assertThat(handle(failing, resetEvent)).isEqualTo(1);
    }

    @Test
    void transit_incident_notifies_site_managers_and_administrators_with_the_known_dispatch() throws Exception {
        int created = handle(events.incident(dispatch, rosa, "VEHICLE_FAILURE", "Llanta baja en Km 40"));

        assertThat(created).isEqualTo(2);
        assertThat(store.notifications).extracting(Notification::recipientUserId).containsExactly(luis, ana);
        assertThat(store.notifications.get(0).title()).isEqualTo("Incidencia en ruta: Falla del vehículo");
        assertThat(store.notifications.get(0).body())
                .isEqualTo("Despacho MAN-0001 hacia Torre Norte: Llanta baja en Km 40");
        assertThat(store.notifications.get(0).link()).isEqualTo("/dispatches/" + dispatch + "/custody");
        assertThat(store.notifications.get(1).link()).isEqualTo("/dispatches/" + dispatch);
    }

    @Test
    void cancelled_dispatch_notifies_the_destination_site_managers() throws Exception {
        String cancelled = events.envelope("DispatchCancelled", "DISPATCH", dispatch, rosa,
                TestEvents.map("dispatchId", dispatch, "orderId", UUID.randomUUID(), "reason", "Pedido duplicado"));

        assertThat(handle(cancelled)).isEqualTo(1);
        assertThat(store.notifications.get(0).body()).endsWith("fue anulado: Pedido duplicado");
        assertThat(store.notifications.get(0).type()).isEqualTo(NotificationType.DISPATCH_CANCELLED);
        assertThat(store.notifications.get(0).link()).isEqualTo("/dispatches/" + dispatch + "/custody");
    }

    @Test
    void reception_with_lines_out_of_tolerance_notifies_the_warehouse_and_administrators() throws Exception {
        assertThat(handle(events.receptionConfirmed(dispatch, almacen, torreNorte, luis, true, true))).isZero();

        int created = handle(events.receptionConfirmed(dispatch, almacen, torreNorte, luis, false, true, false));

        assertThat(created).isEqualTo(2);
        assertThat(store.notifications).extracting(Notification::recipientUserId).containsExactly(rosa, ana);
        assertThat(store.notifications.get(0).body()).contains("2 materiales fuera de la tolerancia");
        assertThat(store.notifications.get(0).link()).isEqualTo("/dispatches/" + dispatch);
    }

    @Test
    void discrepancy_changes_reach_the_opener_the_administrators_and_the_warehouse() throws Exception {
        UUID discrepancy = UUID.randomUUID();

        assertThat(handle(events.discrepancy("DiscrepancyResponded", discrepancy, dispatch, almacen, luis, rosa,
                "Se repone mañana"))).isEqualTo(1);
        assertThat(store.notifications.get(0).recipientUserId()).isEqualTo(luis);
        assertThat(store.notifications.get(0).body()).isEqualTo("Cemento Portland del despacho MAN-0001: Se repone mañana");
        assertThat(store.notifications.get(0).link()).isEqualTo("/discrepancies/" + discrepancy);

        assertThat(handle(events.discrepancy("DiscrepancyEscalated", discrepancy, dispatch, almacen, luis, luis,
                "No coincide"))).isEqualTo(1);
        assertThat(store.notifications.get(1).recipientUserId()).isEqualTo(ana);

        assertThat(handle(events.discrepancy("DiscrepancyResolved", discrepancy, dispatch, almacen, luis, ana,
                "Se descuenta al transportista"))).isEqualTo(2);
        assertThat(store.notifications.subList(2, 4)).extracting(Notification::recipientUserId)
                .containsExactly(rosa, luis);

        assertThat(handle(events.discrepancy("DiscrepancyAccepted", discrepancy, dispatch, almacen, luis, luis,
                null))).isEqualTo(1);
        assertThat(store.notifications.get(4).recipientUserId()).isEqualTo(rosa);
        assertThat(store.notifications.get(4).type()).isEqualTo(NotificationType.DISCREPANCY_ACCEPTED);
    }

    @Test
    void an_unknown_opener_still_gets_the_notification_in_the_inbox_without_email() throws Exception {
        UUID stranger = UUID.randomUUID();
        emails.clear();

        assertThat(handle(events.discrepancy("DiscrepancyResponded", UUID.randomUUID(), dispatch, almacen, stranger,
                rosa, "Ok"))).isEqualTo(1);
        assertThat(store.notifications.get(0).recipientUserId()).isEqualTo(stranger);
        assertThat(store.notifications.get(0).status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(emails).isEmpty();
    }

    @Test
    void events_without_recipients_or_unknown_types_create_nothing() throws Exception {
        assertThat(handle(events.envelope("OrderRegistered", "ORDER", UUID.randomUUID(), null,
                TestEvents.map("orderId", UUID.randomUUID())))).isZero();
        assertThat(handle(events.dispatchDeparted(UUID.randomUUID(), almacen, UUID.randomUUID(), rosa))).isZero();
        assertThat(handle(events.envelope("DispatchDeparted", "DISPATCH", "x", null, TestEvents.map()))).isZero();
        assertThat(handle(events.envelope("DiscrepancyEscalated", "DISCREPANCY", "no-uuid", null,
                TestEvents.map()))).isZero();
        assertThat(handle(events.envelope("UserUpdated", "USER", "x", null, TestEvents.map()))).isZero();
        assertThat(handle(events.envelope("StaffUnassigned", "ASSIGNMENT", "x", null, TestEvents.map()))).isZero();
        assertThat(store.notifications).isEmpty();
    }

    @Test
    void unassigned_or_deactivated_users_stop_receiving_notifications() throws Exception {
        UUID assignment = UUID.randomUUID();
        UUID marta = UUID.randomUUID();
        handle(events.user("UserCreated", marta, "Marta Diaz", "SITE_MANAGER", true));
        handle(events.assigned(assignment, marta, torreNorte, "WORKSITE"));
        handle(events.envelope("StaffUnassigned", "ASSIGNMENT", assignment, null,
                TestEvents.map("assignmentId", assignment)));
        handle(events.user("UserUpdated", luis, "Luis Ramos", "SITE_MANAGER", false));

        assertThat(handle(events.dispatchDeparted(dispatch, almacen, torreNorte, rosa))).isZero();
    }

    @Test
    void the_inbox_shows_only_my_notifications_and_marks_them_read() throws Exception {
        handle(events.incident(dispatch, rosa, "DELAY", "Tráfico"));
        clock.advance(java.time.Duration.ofMinutes(1));
        handle(events.dispatchDeparted(dispatch, almacen, torreNorte, rosa));

        TenantInfo luisSession = new TenantInfo(organization, luis, "SITE_MANAGER");
        var inbox = TenantContext.callAs(luisSession, () -> queries.inbox(false, -1, 0));
        assertThat(inbox.page().total()).isEqualTo(2);
        assertThat(inbox.page().size()).isEqualTo(20);
        assertThat(inbox.unreadCount()).isEqualTo(2);
        assertThat(inbox.page().items().get(0).type()).isEqualTo(NotificationType.DISPATCH_IN_TRANSIT);

        UUID first = inbox.page().items().get(0).id();
        TenantContext.runAs(luisSession, () -> commands.markRead(first));
        TenantContext.runAs(luisSession, () -> commands.markRead(first));
        assertThat(TenantContext.callAs(luisSession, queries::unreadCount)).isEqualTo(1);
        assertThat(TenantContext.callAs(luisSession, () -> queries.inbox(true, 0, 500)).page().total()).isEqualTo(1);

        TenantInfo anaSession = new TenantInfo(organization, ana, "ADMINISTRATOR");
        assertThatThrownBy(() -> TenantContext.runAs(anaSession, () -> commands.markRead(first)))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(TenantContext.callAs(luisSession, commands::markAllRead)).isEqualTo(1);
        assertThat(TenantContext.callAs(luisSession, queries::unreadCount)).isZero();
        assertThat(TenantContext.callAs(anaSession, queries::unreadCount)).isEqualTo(1);
    }

    private int handle(String envelope) throws Exception {
        return handle(commands, envelope);
    }

    private int handle(NotificationCommandService service, String envelope) throws Exception {
        JsonNode node = json.readTree(envelope);
        EventEnvelope event = new EventEnvelope(UUID.fromString(node.get("eventId").asText()),
                node.get("eventType").asText(), Instant.parse(node.get("occurredAt").asText()), organization,
                node.get("aggregateType").asText(), node.get("aggregateId").asText(),
                node.get("actorId").isNull() ? null : UUID.fromString(node.get("actorId").asText()), 1,
                node.get("payload"));
        return TenantContext.callAs(new TenantInfo(organization, TenantContext.SYSTEM_USER_ID, "SYSTEM"),
                () -> service.handle(event));
    }
}
