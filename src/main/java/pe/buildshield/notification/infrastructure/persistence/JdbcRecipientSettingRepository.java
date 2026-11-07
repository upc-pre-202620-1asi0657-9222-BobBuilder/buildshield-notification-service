package pe.buildshield.notification.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.buildshield.notification.domain.model.NotificationType;
import pe.buildshield.notification.domain.model.RecipientSetting;
import pe.buildshield.notification.domain.model.RecipientSettingRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** {@code notification_preferences} y {@code notification_preference_overrides} (US45). Toda consulta filtra por la organización. */
@Repository
class JdbcRecipientSettingRepository implements RecipientSettingRepository {

    private final JdbcTemplate jdbc;

    JdbcRecipientSettingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RecipientSetting> find(UUID organizationId, UUID worksiteId, NotificationType type) {
        return query(" AND o.location_id = ? AND o.type = ?", organizationId, worksiteId, type.name())
                .stream().findFirst();
    }

    @Override
    public List<RecipientSetting> findByWorksite(UUID organizationId, UUID worksiteId) {
        return query(" AND o.location_id = ?", organizationId, worksiteId);
    }

    @Override
    public List<RecipientSetting> findAll(UUID organizationId) {
        return query("", organizationId);
    }

    @Override
    public void save(UUID organizationId, RecipientSetting setting) {
        jdbc.update("""
                INSERT INTO notification_preference_overrides (organization_id, location_id, type, updated_by,
                    updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (organization_id, location_id, type) DO UPDATE SET
                    updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at
                """, organizationId, setting.worksiteId(), setting.type().name(), setting.updatedBy(),
                Timestamp.from(setting.updatedAt()));
        jdbc.update("DELETE FROM notification_preferences WHERE organization_id = ? AND location_id = ? AND type = ?",
                organizationId, setting.worksiteId(), setting.type().name());
        List<Object[]> users = new ArrayList<>();
        for (int i = 0; i < setting.userIds().size(); i++) {
            users.add(new Object[]{UUID.randomUUID(), organizationId, setting.worksiteId(), setting.userIds().get(i),
                    setting.type().name(), i});
        }
        if (!users.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO notification_preferences (id, organization_id, location_id, user_id, type, position)
                    VALUES (?, ?, ?, ?, ?, ?)""", users);
        }
    }

    @Override
    public boolean delete(UUID organizationId, UUID worksiteId, NotificationType type) {
        jdbc.update("DELETE FROM notification_preferences WHERE organization_id = ? AND location_id = ? AND type = ?",
                organizationId, worksiteId, type.name());
        return jdbc.update("""
                DELETE FROM notification_preference_overrides
                WHERE organization_id = ? AND location_id = ? AND type = ?
                """, organizationId, worksiteId, type.name()) > 0;
    }

    private List<RecipientSetting> query(String condition, Object... params) {
        Map<String, Header> headers = new LinkedHashMap<>();
        Map<String, List<UUID>> users = new LinkedHashMap<>();
        jdbc.query("""
                SELECT o.location_id, o.type, o.updated_by, o.updated_at, p.user_id
                FROM notification_preference_overrides o
                LEFT JOIN notification_preferences p ON p.organization_id = o.organization_id
                    AND p.location_id = o.location_id AND p.type = o.type
                WHERE o.organization_id = ?""" + condition + """

                ORDER BY o.location_id, o.type, p.position
                """, rs -> {
                    UUID location = rs.getObject("location_id", UUID.class);
                    String type = rs.getString("type");
                    String key = location + "/" + type;
                    headers.putIfAbsent(key, new Header(location, NotificationType.valueOf(type),
                            rs.getObject("updated_by", UUID.class), rs.getTimestamp("updated_at").toInstant()));
                    List<UUID> list = users.computeIfAbsent(key, k -> new ArrayList<>());
                    UUID user = rs.getObject("user_id", UUID.class);
                    if (user != null) {
                        list.add(user);
                    }
                }, params);
        List<RecipientSetting> found = new ArrayList<>();
        headers.forEach((key, header) -> found.add(new RecipientSetting(header.worksiteId(), header.type(),
                users.get(key), header.updatedBy(), header.updatedAt())));
        return found;
    }

    private record Header(UUID worksiteId, NotificationType type, UUID updatedBy, Instant updatedAt) {
    }
}
