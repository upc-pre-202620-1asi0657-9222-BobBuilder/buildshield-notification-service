package pe.buildshield.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.buildshield.notification.domain.model.Notification;
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
    private final NotificationCommandService commands = new NotificationCommandService(store, store, store,
            (to, subject, body) -> emails.add(to + "|" + subject), clock);
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
        JsonNode node = json.readTree(envelope);
        EventEnvelope event = new EventEnvelope(UUID.fromString(node.get("eventId").asText()),
                node.get("eventType").asText(), Instant.parse(node.get("occurredAt").asText()), organization,
                node.get("aggregateType").asText(), node.get("aggregateId").asText(),
                node.get("actorId").isNull() ? null : UUID.fromString(node.get("actorId").asText()), 1,
                node.get("payload"));
        return TenantContext.callAs(new TenantInfo(organization, TenantContext.SYSTEM_USER_ID, "SYSTEM"),
                () -> commands.handle(event));
    }
}
