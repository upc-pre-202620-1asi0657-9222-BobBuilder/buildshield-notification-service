package pe.buildshield.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.buildshield.notification.application.RecipientSettingsService.TypeSettings;
import pe.buildshield.notification.domain.model.Notification;
import pe.buildshield.notification.domain.model.NotificationType;
import pe.buildshield.notification.domain.model.Recipient;
import pe.buildshield.notification.domain.model.RecipientSetting;
import pe.buildshield.notification.shared.error.ResourceNotFoundException;
import pe.buildshield.notification.shared.error.ValidationException;
import pe.buildshield.notification.shared.messaging.EventEnvelope;
import pe.buildshield.notification.shared.tenant.TenantContext;
import pe.buildshield.notification.shared.tenant.TenantInfo;
import pe.buildshield.notification.support.MutableClock;
import pe.buildshield.notification.support.TestEvents;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US45: configuración de destinatarios por obra y tipo de aviso, y su efecto al enviar. */
class RecipientSettingsApplicationTest {

    private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");

    private final ObjectMapper json = new ObjectMapper();
    private final UUID organization = UUID.randomUUID();
    private final TestEvents events = new TestEvents(json, organization);
    private final InMemoryNotifications store = new InMemoryNotifications();
    private final InMemoryRecipientSettings repository = new InMemoryRecipientSettings();
    private final MutableClock clock = new MutableClock(NOW);
    private final NotificationCommandService commands = new NotificationCommandService(store, store, store,
            repository, (to, subject, body) -> { }, clock);
    private final RecipientSettingsService settings = new RecipientSettingsService(repository, store, store, clock);

    private final UUID ana = UUID.randomUUID();
    private final UUID rosa = UUID.randomUUID();
    private final UUID luis = UUID.randomUUID();
    private final UUID pedro = UUID.randomUUID();
    private final UUID torreNorte = UUID.randomUUID();
    private final UUID otraObra = UUID.randomUUID();
    private final UUID almacen = UUID.randomUUID();
    private final UUID dispatch = UUID.randomUUID();
    private final UUID luisAssignment = UUID.randomUUID();

    @BeforeEach
    void organizationWithStaff() throws Exception {
        handle(events.user("UserCreated", ana, "Ana Torres", "ADMINISTRATOR", true));
        handle(events.user("UserCreated", rosa, "Rosa Quispe", "WAREHOUSE_MANAGER", true));
        handle(events.user("UserCreated", luis, "Luis Ramos", "SITE_MANAGER", true));
        handle(events.user("UserCreated", pedro, "Pedro Soto", "SITE_MANAGER", true));
        handle(events.location(torreNorte, "WORKSITE", "Torre Norte"));
        handle(events.location(otraObra, "WORKSITE", "Edificio Sur"));
        handle(events.location(almacen, "WAREHOUSE", "Almacén Central"));
        handle(events.assigned(luisAssignment, luis, torreNorte, "WORKSITE"));
        handle(events.assigned(UUID.randomUUID(), pedro, otraObra, "WORKSITE"));
        handle(events.assigned(UUID.randomUUID(), rosa, almacen, "WAREHOUSE"));
        handle(events.dispatchPrepared(dispatch, "MAN-0001", almacen, torreNorte, rosa));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void a_worksite_without_configuration_uses_the_default_rules_and_lists_who_can_be_chosen() {
        var view = asAdmin(() -> settings.forWorksite(torreNorte));

        assertThat(view.worksiteName()).isEqualTo("Torre Norte");
        assertThat(view.types()).hasSize(NotificationType.values().length)
                .allSatisfy(type -> assertThat(type.configured()).isFalse());
        assertThat(view.types().get(0).defaultAudience()).isEqualTo("Encargados de obra asignados a la obra");
        assertThat(view.candidates()).extracting(Recipient::fullName)
                .containsExactly("Ana Torres", "Luis Ramos", "Rosa Quispe");
        assertThat(asAdmin(settings::list)).isEmpty();
    }

    @Test
    void the_administrator_chooses_who_receives_each_notification_of_a_worksite() throws Exception {
        TypeSettings saved = asAdmin(() -> settings.replace(torreNorte, NotificationType.DISPATCH_IN_TRANSIT,
                List.of(ana, rosa, ana)));

        assertThat(saved.configured()).isTrue();
        assertThat(saved.recipients()).extracting(Recipient::userId).containsExactly(ana, rosa);
        assertThat(saved.updatedBy()).isEqualTo(ana);
        assertThat(saved.updatedAt()).isEqualTo(NOW);

        // Rosa provoca el evento: no se le avisa a ella; Luis ya no recibe porque la obra tiene otra configuración.
        assertThat(handle(events.dispatchDeparted(dispatch, almacen, torreNorte, rosa))).isEqualTo(1);
        assertThat(store.notifications).extracting(Notification::recipientUserId).containsExactly(ana);

        var listed = asAdmin(settings::list);
        assertThat(listed).singleElement().satisfies(configured -> {
            assertThat(configured.worksiteName()).isEqualTo("Torre Norte");
            assertThat(configured.setting().type()).isEqualTo(NotificationType.DISPATCH_IN_TRANSIT);
        });
        var view = asAdmin(() -> settings.forWorksite(torreNorte));
        assertThat(view.types().stream().filter(TypeSettings::configured)).hasSize(1);
    }

    @Test
    void the_configuration_applies_to_every_event_type_of_the_worksite() throws Exception {
        asAdmin(() -> settings.replace(torreNorte, NotificationType.TRANSIT_INCIDENT, List.of(rosa)));
        asAdmin(() -> settings.replace(torreNorte, NotificationType.RECEPTION_WITH_DISCREPANCIES, List.of(luis)));
        asAdmin(() -> settings.replace(torreNorte, NotificationType.DISPATCH_CANCELLED, List.of()));
        asAdmin(() -> settings.replace(torreNorte, NotificationType.DISCREPANCY_ESCALATED, List.of(rosa)));

        assertThat(handle(events.incident(dispatch, luis, "DELAY", "Tráfico"))).isEqualTo(1);
        assertThat(handle(events.receptionConfirmed(dispatch, almacen, torreNorte, ana, false))).isEqualTo(1);
        assertThat(handle(events.envelope("DispatchCancelled", "DISPATCH", dispatch, ana,
                TestEvents.map("dispatchId", dispatch, "reason", "Duplicado")))).isZero();
        assertThat(handle(events.discrepancy("DiscrepancyEscalated", UUID.randomUUID(), dispatch, almacen, luis,
                luis, "No coincide"))).isEqualTo(1);

        assertThat(store.notifications).extracting(Notification::recipientUserId).containsExactly(rosa, luis, rosa);
    }

    @Test
    void the_opener_of_a_discrepancy_keeps_receiving_its_notifications() throws Exception {
        asAdmin(() -> settings.replace(torreNorte, NotificationType.DISCREPANCY_RESPONDED, List.of()));
        asAdmin(() -> settings.replace(torreNorte, NotificationType.DISCREPANCY_RESOLVED, List.of(ana)));
        UUID discrepancy = UUID.randomUUID();

        assertThat(handle(events.discrepancy("DiscrepancyResponded", discrepancy, dispatch, almacen, luis, rosa,
                "Se repone"))).isEqualTo(1);
        assertThat(handle(events.discrepancy("DiscrepancyResolved", discrepancy, dispatch, almacen, luis, rosa,
                "Cerrada"))).isEqualTo(2);
        // El evento trae la obra en el payload: no hace falta la copia del despacho.
        String withWorksite = events.envelope("DiscrepancyAccepted", "DISCREPANCY", discrepancy, luis,
                TestEvents.map("discrepancyId", discrepancy, "worksiteId", otraObra, "warehouseId", almacen,
                        "openedBy", luis));
        asAdmin(() -> settings.replace(otraObra, NotificationType.DISCREPANCY_ACCEPTED, List.of(ana)));
        assertThat(handle(withWorksite)).isEqualTo(1);

        assertThat(store.notifications).extracting(Notification::recipientUserId)
                .containsExactly(luis, ana, luis, ana);
    }

    @Test
    void deactivated_or_unassigned_chosen_users_stop_receiving_and_reset_restores_the_default() throws Exception {
        asAdmin(() -> settings.replace(torreNorte, NotificationType.DISPATCH_IN_TRANSIT, List.of(luis, ana)));
        handle(events.user("UserUpdated", ana, "Ana Torres", "ADMINISTRATOR", false));
        handle(events.envelope("StaffUnassigned", "ASSIGNMENT", luisAssignment, null,
                TestEvents.map("assignmentId", luisAssignment)));

        assertThat(handle(events.dispatchDeparted(dispatch, almacen, torreNorte, rosa))).isZero();
        var view = asAdmin(() -> settings.forWorksite(torreNorte));
        assertThat(view.types().get(0).recipients()).extracting(Recipient::active).containsExactly(true, false);

        handle(events.assigned(UUID.randomUUID(), luis, torreNorte, "WORKSITE"));
        asAdmin(() -> {
            settings.reset(torreNorte, NotificationType.DISPATCH_IN_TRANSIT);
            settings.reset(torreNorte, NotificationType.DISPATCH_IN_TRANSIT);
            return null;
        });
        assertThat(handle(events.dispatchDeparted(dispatch, almacen, torreNorte, rosa))).isEqualTo(1);
        assertThat(store.notifications.get(0).recipientUserId()).isEqualTo(luis);
    }

    @Test
    void invalid_recipients_and_unknown_worksites_are_rejected() throws Exception {
        handle(events.user("UserCreated", UUID.fromString("00000000-0000-0000-0000-00000000000a"), "Inactivo",
                "WAREHOUSE_MANAGER", false));

        assertThatThrownBy(() -> asAdmin(() -> settings.replace(torreNorte, NotificationType.DISPATCH_IN_TRANSIT,
                List.of(pedro)))).isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "RECIPIENT_NOT_ASSIGNED");
        assertThatThrownBy(() -> asAdmin(() -> settings.replace(torreNorte, NotificationType.DISPATCH_IN_TRANSIT,
                List.of(UUID.randomUUID(), UUID.fromString("00000000-0000-0000-0000-00000000000a")))))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "UNKNOWN_RECIPIENT");
        List<UUID> many = IntStream.range(0, RecipientSetting.MAX_RECIPIENTS + 1).mapToObj(i -> UUID.randomUUID())
                .toList();
        assertThatThrownBy(() -> asAdmin(() -> settings.replace(torreNorte, NotificationType.DISPATCH_IN_TRANSIT,
                many))).isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "TOO_MANY_RECIPIENTS");
        assertThatThrownBy(() -> asAdmin(() -> settings.replace(torreNorte, NotificationType.DISPATCH_IN_TRANSIT,
                Arrays.asList(ana, null)))).isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_RECIPIENTS");
        assertThatThrownBy(() -> asAdmin(() -> settings.forWorksite(almacen)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> asAdmin(() -> {
            settings.reset(UUID.randomUUID(), NotificationType.DISPATCH_IN_TRANSIT);
            return null;
        })).isInstanceOf(ResourceNotFoundException.class);

        TypeSettings empty = asAdmin(() -> settings.replace(torreNorte, NotificationType.TRANSIT_INCIDENT, null));
        assertThat(empty.configured()).isTrue();
        assertThat(empty.recipients()).isEmpty();
        assertThat(repository.settings).hasSize(1);
    }

    @Test
    void a_setting_keeps_its_users_in_order_and_without_repeats() {
        UUID worksite = UUID.randomUUID();
        RecipientSetting setting = new RecipientSetting(worksite, NotificationType.DISPATCH_CANCELLED,
                new ArrayList<>(List.of(rosa, ana, rosa)), ana, NOW);

        assertThat(setting.userIds()).containsExactly(rosa, ana);
        assertThat(NotificationType.DISCREPANCY_ACCEPTED.label()).isEqualTo("Discrepancia aceptada");
    }

    private <T> T asAdmin(java.util.function.Supplier<T> action) {
        return TenantContext.callAs(new TenantInfo(organization, ana, "ADMINISTRATOR"), action);
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
