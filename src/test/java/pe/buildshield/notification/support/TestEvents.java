package pe.buildshield.notification.support;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sobres JSON de eventos de los demás servicios, como los publica su outbox (plan §2 y §5). */
public final class TestEvents {

    private final ObjectMapper json;
    private final UUID organization;

    public TestEvents(ObjectMapper json, UUID organization) {
        this.json = json;
        this.organization = organization;
    }

    public String envelope(String eventType, String aggregateType, Object aggregateId, UUID actorId,
            Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID());
        envelope.put("eventType", eventType);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("organizationId", organization);
        envelope.put("aggregateType", aggregateType);
        envelope.put("aggregateId", aggregateId.toString());
        envelope.put("actorId", actorId);
        envelope.put("version", 1);
        envelope.put("payload", payload);
        try {
            return json.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public String user(String eventType, UUID userId, String fullName, String role, boolean active) {
        return envelope(eventType, "USER", userId, null, map("userId", userId, "fullName", fullName, "email",
                fullName.toLowerCase().replace(' ', '.') + "@obra.pe", "role", role, "active", active));
    }

    public String location(UUID locationId, String type, String name) {
        return envelope("LocationRegistered", "LOCATION", locationId, null, map("locationId", locationId, "type", type,
                "name", name, "address", "Av. 1", "active", true));
    }

    public String assigned(UUID assignmentId, UUID userId, UUID locationId, String locationType) {
        return envelope("StaffAssigned", "ASSIGNMENT", assignmentId, null, map("assignmentId", assignmentId,
                "userId", userId, "locationId", locationId, "locationType", locationType));
    }

    public String dispatchPrepared(UUID dispatchId, String manifest, UUID warehouseId, UUID worksiteId, UUID actor) {
        return envelope("DispatchPrepared", "DISPATCH", dispatchId, actor, map("dispatchId", dispatchId,
                "orderId", UUID.randomUUID(), "manifestCode", manifest, "warehouseId", warehouseId, "worksiteId",
                worksiteId, "lines", List.of()));
    }

    public String dispatchDeparted(UUID dispatchId, UUID warehouseId, UUID worksiteId, UUID actor) {
        return envelope("DispatchDeparted", "DISPATCH", dispatchId, actor, map("dispatchId", dispatchId, "orderId",
                UUID.randomUUID(), "warehouseId", warehouseId, "worksiteId", worksiteId, "departedAt",
                Instant.now().toString(), "lines", List.of()));
    }

    public String incident(UUID dispatchId, UUID actor, String type, String observation) {
        return envelope("TransitIncidentReported", "DISPATCH", dispatchId, actor, map("dispatchId", dispatchId,
                "incidentId", UUID.randomUUID(), "type", type, "observation", observation));
    }

    public String receptionConfirmed(UUID dispatchId, UUID warehouseId, UUID worksiteId, UUID actor,
            boolean... withinTolerance) {
        List<Map<String, Object>> lines = new java.util.ArrayList<>();
        for (boolean within : withinTolerance) {
            lines.add(map("receptionLineId", UUID.randomUUID(), "materialId", UUID.randomUUID(), "requested", 10,
                    "dispatched", 10, "received", within ? 10 : 7, "withinTolerance", within));
        }
        UUID receptionId = UUID.randomUUID();
        return envelope("ReceptionConfirmed", "RECEPTION", receptionId, actor, map("receptionId", receptionId,
                "dispatchId", dispatchId, "orderId", UUID.randomUUID(), "worksiteId", worksiteId, "warehouseId",
                warehouseId, "confirmedBy", actor, "lines", lines));
    }

    public String discrepancy(String eventType, UUID discrepancyId, UUID dispatchId, UUID warehouseId, UUID openedBy,
            UUID actor, String text) {
        return envelope(eventType, "DISCREPANCY", discrepancyId, actor, map("discrepancyId", discrepancyId,
                "dispatchId", dispatchId, "warehouseId", warehouseId, "materialName", "Cemento Portland", "sku",
                "CEM-001", "manifestCode", "MAN-0001", "openedBy", openedBy, "status", "RESPONDED", "text", text));
    }

    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
