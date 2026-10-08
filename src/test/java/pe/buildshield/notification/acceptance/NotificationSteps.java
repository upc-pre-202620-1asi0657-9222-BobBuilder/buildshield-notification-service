package pe.buildshield.notification.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import pe.buildshield.notification.shared.security.JwtTokenIssuer;
import pe.buildshield.notification.shared.tenant.TenantInfo;
import pe.buildshield.notification.support.TestEvents;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static pe.buildshield.notification.acceptance.ApiRequest.get;
import static pe.buildshield.notification.acceptance.ApiRequest.post;

/** Pasos de US43 (notificación de eventos): los hechos llegan por Kafka y la bandeja se consulta por HTTP. */
public class NotificationSteps {

    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    ScenarioSession session;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtTokenIssuer issuer;

    @Autowired
    ObjectMapper json;

    private final UUID organization = UUID.randomUUID();
    private final Map<String, UUID> users = new HashMap<>();
    private final Map<String, String> roles = new HashMap<>();
    private final Map<String, UUID> places = new HashMap<>();
    private final Map<String, UUID> dispatches = new HashMap<>();
    private final Map<String, UUID> discrepancies = new HashMap<>();
    private long processedBefore;

    private TestEvents events() {
        return new TestEvents(json, organization);
    }

    @Dado("que en la organización trabajan {string} como {string}")
    public void userExists(String name, String role) {
        UUID id = UUID.randomUUID();
        users.put(name, id);
        roles.put(name, role(role));
        send("buildshield.iam", id, events().user("UserCreated", id, name, role(role), true));
    }

    @Dado("que existen la obra {string} y el almacén {string}")
    public void places(String worksite, String warehouse) {
        places.put(worksite, UUID.randomUUID());
        places.put(warehouse, UUID.randomUUID());
        send("buildshield.organization", places.get(worksite), events().location(places.get(worksite), "WORKSITE",
                worksite));
        send("buildshield.organization", places.get(warehouse), events().location(places.get(warehouse), "WAREHOUSE",
                warehouse));
    }

    @Dado("que {string} está asignado a {string}")
    public void assigned(String name, String place) {
        UUID assignment = UUID.randomUUID();
        String type = roles.get(name).equals("WAREHOUSE_MANAGER") ? "WAREHOUSE" : "WORKSITE";
        send("buildshield.organization", assignment, events().assigned(assignment, users.get(name),
                places.computeIfAbsent(place, ignored -> UUID.randomUUID()), type));
    }

    @Dado("que {string} preparó el despacho {string} del {string} a {string}")
    public void prepared(String name, String manifest, String warehouse, String worksite) {
        UUID dispatch = UUID.randomUUID();
        dispatches.put(manifest, dispatch);
        send("buildshield.ordering", dispatch, events().dispatchPrepared(dispatch, manifest, places.get(warehouse),
                places.get(worksite), users.get(name)));
    }

    @Cuando("{string} despacha {string} hacia la obra")
    public void departed(String name, String manifest) {
        DispatchRef d = dispatch(manifest);
        send("buildshield.ordering", d.id, events().dispatchDeparted(d.id, d.warehouse, d.worksite, users.get(name)));
    }

    @Cuando("{string} registra la incidencia {string} con la observación {string} en {string}")
    public void incident(String name, String type, String observation, String manifest) {
        send("buildshield.ordering", dispatch(manifest).id, events().incident(dispatch(manifest).id, users.get(name),
                type, observation));
    }

    @Cuando("{string} confirma la recepción de {string} con {int} material(es) fuera de la tolerancia")
    public void receptionConfirmed(String name, String manifest, int outOfTolerance) {
        DispatchRef d = dispatch(manifest);
        boolean[] lines = new boolean[outOfTolerance + 1];
        lines[0] = true;
        send("buildshield.ordering", d.id, events().receptionConfirmed(d.id, d.warehouse, d.worksite, users.get(name),
                lines));
    }

    @Cuando("{string} {string} la discrepancia de {string} abierta por {string} con el texto {string}")
    public void discrepancy(String name, String action, String manifest, String opener, String text) {
        String eventType = switch (action) {
            case "responde" -> "DiscrepancyResponded";
            case "escala" -> "DiscrepancyEscalated";
            case "resuelve" -> "DiscrepancyResolved";
            default -> "DiscrepancyAccepted";
        };
        DispatchRef d = dispatch(manifest);
        UUID discrepancy = discrepancies.computeIfAbsent(manifest, ignored -> UUID.randomUUID());
        send("buildshield.discrepancy", discrepancy, events().discrepancy(eventType, discrepancy, d.id, d.warehouse,
                users.get(opener), users.get(name), text));
    }

    @Entonces("{string} tiene {int} notificación/notificaciones sin leer")
    public void unread(String name, int count) throws Exception {
        awaitEvents();
        signIn(name);
        session.send(get("/api/v1/notifications/unread-count"), null);
        session.expectStatus(200);
        assertThat(session.body().path("unreadCount").asInt()).isEqualTo(count);
    }

    @Entonces("la última notificación de {string} dice {string} y lleva a {string}")
    public void lastNotification(String name, String title, String link) throws Exception {
        awaitEvents();
        signIn(name);
        session.send(get("/api/v1/notifications"), null);
        session.expectStatus(200);
        JsonNode first = session.body().path("items").get(0);
        assertThat(first.path("title").asText()).isEqualTo(title);
        String expectedLink = link;
        for (Map.Entry<String, UUID> entry : dispatches.entrySet()) {
            expectedLink = expectedLink.replace("{" + entry.getKey() + "}", entry.getValue().toString());
        }
        for (Map.Entry<String, UUID> entry : discrepancies.entrySet()) {
            expectedLink = expectedLink.replace("{discrepancia " + entry.getKey() + "}", entry.getValue().toString());
        }
        assertThat(first.path("link").asText()).isEqualTo(expectedLink);
    }

    @Cuando("{string} marca como leída su última notificación")
    public void markLast(String name) throws Exception {
        awaitEvents();
        signIn(name);
        session.send(get("/api/v1/notifications"), null);
        String id = session.body().path("items").get(0).path("id").asText();
        session.send(post("/api/v1/notifications/" + id + "/read"), null);
        session.expectStatus(204);
        session.put("última de " + name, UUID.fromString(id));
    }

    @Cuando("{string} marca todas sus notificaciones como leídas")
    public void markAll(String name) throws Exception {
        awaitEvents();
        signIn(name);
        session.send(post("/api/v1/notifications/read-all"), null);
        session.expectStatus(204);
    }

    @Entonces("{string} no puede marcar la última notificación de {string}")
    public void cannotMarkOthers(String name, String owner) throws Exception {
        signIn(name);
        session.send(post("/api/v1/notifications/" + session.id("última de " + owner) + "/read"), null);
        session.expectStatus(404);
    }

    @Entonces("la bandeja de {string} solo muestra no leídas: {int}")
    public void unreadOnly(String name, int count) throws Exception {
        signIn(name);
        session.send(get("/api/v1/notifications").param("unreadOnly", "true"), null);
        session.expectStatus(200);
        assertThat(session.body().path("items").size()).isEqualTo(count);
        List<String> read = new ArrayList<>();
        session.body().path("items").forEach(item -> {
            if (!item.path("readAt").isNull()) {
                read.add(item.path("id").asText());
            }
        });
        assertThat(read).isEmpty();
    }

    private void send(String topic, UUID key, String message) {
        if (topic.equals("buildshield.ordering") || topic.equals("buildshield.discrepancy")) {
            // Cada tópico tiene su consumidor: las copias (usuarios, lugares, asignaciones) deben estar al día antes
            // del hecho que genera el aviso, como ocurre en producción con eventos separados en el tiempo.
            awaitEvents();
        }
        kafka.send(topic, key.toString(), message);
        processedBefore++;
    }

    private void awaitEvents() {
        await().atMost(EVENT_TIMEOUT).until(() -> jdbc.queryForObject("SELECT count(*) FROM processed_events",
                Long.class) >= processedBefore);
    }

    private void signIn(String name) {
        session.accessToken(issuer.issue(new TenantInfo(organization, users.get(name), roles.get(name))).value());
    }

    private DispatchRef dispatch(String manifest) {
        return new DispatchRef(dispatches.get(manifest), placeFor(manifest, "WAREHOUSE"),
                placeFor(manifest, "WORKSITE"));
    }

    private UUID placeFor(String manifest, String type) {
        return jdbc.query("SELECT worksite_id, warehouse_id FROM dispatch_projection WHERE dispatch_id = ?",
                rs -> rs.next() ? rs.getObject(type.equals("WORKSITE") ? "worksite_id" : "warehouse_id", UUID.class)
                        : null, awaitDispatch(manifest));
    }

    private UUID awaitDispatch(String manifest) {
        UUID id = dispatches.get(manifest);
        await().atMost(EVENT_TIMEOUT).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM dispatch_projection WHERE dispatch_id = ?", Long.class, id) == 1);
        return id;
    }

    private static String role(String displayName) {
        return switch (displayName.trim().toLowerCase()) {
            case "administrador" -> "ADMINISTRATOR";
            case "encargado de obra" -> "SITE_MANAGER";
            case "encargado de almacén", "encargado de almacen" -> "WAREHOUSE_MANAGER";
            default -> displayName;
        };
    }

    private record DispatchRef(UUID id, UUID warehouse, UUID worksite) {
    }
}
