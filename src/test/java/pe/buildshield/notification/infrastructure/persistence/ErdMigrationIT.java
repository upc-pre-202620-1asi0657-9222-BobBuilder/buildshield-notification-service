package pe.buildshield.notification.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * V3 alinea el esquema con el ER sin perder datos. Corre en un esquema aparte de la base temporal externa
 * {@code notification_it} (se activa con {@code -Dbuildshield.test.external-database=true}).
 */
class ErdMigrationIT {

    private static final String SCHEMA = "erd_migration_it";

    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;

    @BeforeEach
    void connect() {
        assumeTrue(Boolean.getBoolean("buildshield.test.external-database"),
                "Requiere la base temporal externa notification_it");
        dataSource = new DriverManagerDataSource(
                System.getProperty("buildshield.test.notification-url", "jdbc:postgresql://127.0.0.1:5433/notification_it"),
                System.getProperty("buildshield.test.database-user", "buildshield_core"),
                System.getProperty("buildshield.test.database-password", ""));
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @AfterEach
    void drop() {
        if (jdbc != null) {
            jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource).schemas(SCHEMA).defaultSchema(SCHEMA).target(target).load();
    }

    @Test
    void v3_keeps_notifications_preferences_and_processed_events() {
        flyway("2").migrate();
        UUID organization = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID worksite = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        UUID setting = UUID.randomUUID();
        UUID emptySetting = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        jdbc.update("INSERT INTO " + SCHEMA + ".notifications (id, organization_id, recipient_user_id, type, title, "
                + "body, link, source_event_id, created_at) VALUES (?, ?, ?, 'TRANSIT_INCIDENT', 'Incidencia', "
                + "'Retraso', '/dispatches/1', ?, now())", UUID.randomUUID(), organization, user, event);
        jdbc.update("INSERT INTO " + SCHEMA + ".processed_events (event_id, processed_at) VALUES (?, now())", event);
        jdbc.update("INSERT INTO " + SCHEMA + ".recipient_settings (id, organization_id, worksite_id, "
                + "notification_type, updated_by, updated_at) VALUES (?, ?, ?, 'TRANSIT_INCIDENT', ?, now())",
                setting, organization, worksite, admin);
        jdbc.update("INSERT INTO " + SCHEMA + ".recipient_setting_users (setting_id, user_id, position) VALUES "
                + "(?, ?, 0), (?, ?, 1)", setting, other, setting, user);
        jdbc.update("INSERT INTO " + SCHEMA + ".recipient_settings (id, organization_id, worksite_id, "
                + "notification_type, updated_by, updated_at) VALUES (?, ?, ?, 'DISPATCH_CANCELLED', ?, now())",
                emptySetting, organization, worksite, admin);

        flyway("latest").migrate();

        Map<String, Object> notification = jdbc.queryForMap("SELECT user_id, payload::text AS payload, status, "
                + "attempts, sent_at IS NOT NULL AS sent FROM " + SCHEMA + ".notifications");
        assertThat(notification.get("user_id")).isEqualTo(user);
        assertThat((String) notification.get("payload")).contains("\"title\": \"Incidencia\"")
                .contains("\"body\": \"Retraso\"").contains("\"link\": \"/dispatches/1\"");
        assertThat(notification.get("status")).isEqualTo("SENT");
        assertThat(notification.get("attempts")).isEqualTo(1);
        assertThat(notification.get("sent")).isEqualTo(true);

        List<UUID> preferred = jdbc.queryForList("SELECT user_id FROM " + SCHEMA + ".notification_preferences "
                + "WHERE location_id = ? AND type = 'TRANSIT_INCIDENT' ORDER BY position", UUID.class, worksite);
        assertThat(preferred).containsExactly(other, user);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".notification_preference_overrides",
                Integer.class)).isEqualTo(2);

        // El evento ya procesado sigue ignorado para cada consumidor: sin source_event_id, esto evita avisos repetidos.
        assertThat(jdbc.queryForList("SELECT consumer FROM " + SCHEMA + ".processed_events WHERE event_id = ?",
                String.class, event)).containsExactlyInAnyOrder("IamEventConsumer", "OrganizationEventConsumer",
                "OrderingEventConsumer", "DiscrepancyEventConsumer");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = ? "
                + "AND table_name IN ('recipient_settings', 'recipient_setting_users')", Integer.class, SCHEMA))
                .isZero();
    }
}
