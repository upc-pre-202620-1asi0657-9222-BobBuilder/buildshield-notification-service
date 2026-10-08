package pe.buildshield.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import pe.buildshield.notification.application.NotificationCommandService;
import pe.buildshield.notification.application.NotificationQueryService;
import pe.buildshield.notification.shared.tenant.TenantContext;
import pe.buildshield.notification.shared.tenant.TenantInfo;
import pe.buildshield.notification.support.NotificationIntegrationTest;
import pe.buildshield.notification.support.TestDatabase;
import pe.buildshield.notification.support.TestEvents;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Los avisos nacen solo de eventos (Kafka embebido y PostgreSQL real): copias de iam y organization, despacho en
 * tránsito e incidencia (que solo trae el dispatchId). Un evento repetido no duplica avisos y cada usuario ve solo
 * su bandeja de su organización.
 */
@NotificationIntegrationTest
class NotificationMessagingIT {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    @Autowired
    NotificationQueryService queries;

    @Autowired
    NotificationCommandService commands;

    @BeforeEach
    void clean() {
        TestDatabase.reset(jdbc);
    }

    @Test
    void dispatch_events_notify_the_destination_staff_once_and_only_in_their_inbox() {
        UUID organization = UUID.randomUUID();
        TestEvents events = new TestEvents(json, organization);
        UUID ana = UUID.randomUUID();
        UUID luis = UUID.randomUUID();
        UUID worksite = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        UUID dispatch = UUID.randomUUID();
        UUID rosa = UUID.randomUUID();

        kafka.send("buildshield.iam", ana.toString(), events.user("UserCreated", ana, "Ana Torres", "ADMINISTRATOR",
                true));
        kafka.send("buildshield.iam", luis.toString(), events.user("UserCreated", luis, "Luis Ramos", "SITE_MANAGER",
                true));
        kafka.send("buildshield.organization", worksite.toString(), events.location(worksite, "WORKSITE",
                "Torre Norte"));
        UUID assignment = UUID.randomUUID();
        kafka.send("buildshield.organization", assignment.toString(), events.assigned(assignment, luis, worksite,
                "WORKSITE"));
        await().atMost(Duration.ofSeconds(30)).until(() -> count("assignment_projection") == 1
                && count("recipient_projection") == 2 && count("location_projection") == 1);

        kafka.send("buildshield.ordering", dispatch.toString(), events.dispatchPrepared(dispatch, "MAN-0007", warehouse,
                worksite, rosa));
        String departed = events.dispatchDeparted(dispatch, warehouse, worksite, rosa);
        kafka.send("buildshield.ordering", dispatch.toString(), departed);
        kafka.send("buildshield.ordering", dispatch.toString(), departed);
        kafka.send("buildshield.ordering", dispatch.toString(), events.incident(dispatch, rosa, "DELAY", "Tráfico"));

        await().atMost(Duration.ofSeconds(30)).until(() -> count("notifications") == 3);
        await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5)).until(() -> count("notifications") == 3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE organization_id = ?", Long.class,
                organization)).isEqualTo(3);

        TenantInfo luisSession = new TenantInfo(organization, luis, "SITE_MANAGER");
        var inbox = TenantContext.callAs(luisSession, () -> queries.inbox(false, 0, 20));
        assertThat(inbox.page().total()).isEqualTo(2);
        assertThat(inbox.page().items()).extracting(n -> n.title())
                .containsExactlyInAnyOrder("Despacho en camino a Torre Norte", "Incidencia en ruta: Retraso");
        assertThat(inbox.page().items()).allSatisfy(n -> assertThat(n.body()).contains("MAN-0007"));

        TenantInfo stranger = new TenantInfo(UUID.randomUUID(), luis, "SITE_MANAGER");
        assertThat(TenantContext.callAs(stranger, () -> queries.inbox(false, 0, 20)).page().total()).isZero();

        TenantContext.runAs(luisSession, () -> commands.markRead(inbox.page().items().get(0).id()));
        assertThat(TenantContext.callAs(luisSession, queries::unreadCount)).isEqualTo(1);
        assertThat(TenantContext.callAs(luisSession, commands::markAllRead)).isEqualTo(1);
        assertThat(TenantContext.callAs(new TenantInfo(organization, ana, "ADMINISTRATOR"), queries::unreadCount))
                .isEqualTo(1);
    }

    private long count(String table) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return count == null ? 0 : count;
    }
}
