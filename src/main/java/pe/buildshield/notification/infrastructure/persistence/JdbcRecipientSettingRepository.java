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

/** {@code recipient_settings} y {@code recipient_setting_users} (US45). Toda consulta filtra por la organización. */
@Repository
class JdbcRecipientSettingRepository implements RecipientSettingRepository {

    private final JdbcTemplate jdbc;

    JdbcRecipientSettingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RecipientSetting> find(UUID organizationId, UUID worksiteId, NotificationType type) {
        return query(" AND s.worksite_id = ? AND s.notification_type = ?", organizationId, worksiteId, type.name())
                .stream().findFirst();
    }

    @Override
    public List<RecipientSetting> findByWorksite(UUID organizationId, UUID worksiteId) {
        return query(" AND s.worksite_id = ?", organizationId, worksiteId);
    }

    @Override
    public List<RecipientSetting> findAll(UUID organizationId) {
        return query("", organizationId);
    }

    @Override
    public void save(UUID organizationId, RecipientSetting setting) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO recipient_settings (id, organization_id, worksite_id, notification_type, updated_by,
                    updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (organization_id, worksite_id, notification_type) DO UPDATE SET
                    updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at
                RETURNING id
                """, UUID.class, UUID.randomUUID(), organizationId, setting.worksiteId(), setting.type().name(),
                setting.updatedBy(), Timestamp.from(setting.updatedAt()));
        jdbc.update("DELETE FROM recipient_setting_users WHERE setting_id = ?", id);
        List<Object[]> users = new ArrayList<>();
        for (int i = 0; i < setting.userIds().size(); i++) {
            users.add(new Object[]{id, setting.userIds().get(i), i});
        }
        if (!users.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO recipient_setting_users (setting_id, user_id, position) VALUES (?, ?, ?)",
                    users);
        }
    }

    @Override
    public boolean delete(UUID organizationId, UUID worksiteId, NotificationType type) {
        return jdbc.update("""
                DELETE FROM recipient_settings
                WHERE organization_id = ? AND worksite_id = ? AND notification_type = ?
                """, organizationId, worksiteId, type.name()) > 0;
    }

    private List<RecipientSetting> query(String condition, Object... params) {
        Map<UUID, Header> headers = new LinkedHashMap<>();
        Map<UUID, List<UUID>> users = new LinkedHashMap<>();
        jdbc.query("""
                SELECT s.id, s.worksite_id, s.notification_type, s.updated_by, s.updated_at, u.user_id
                FROM recipient_settings s
                LEFT JOIN recipient_setting_users u ON u.setting_id = s.id
                WHERE s.organization_id = ?""" + condition + """

                ORDER BY s.worksite_id, s.notification_type, u.position
                """, rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    headers.putIfAbsent(id, new Header(rs.getObject("worksite_id", UUID.class),
                            NotificationType.valueOf(rs.getString("notification_type")),
                            rs.getObject("updated_by", UUID.class), rs.getTimestamp("updated_at").toInstant()));
                    List<UUID> list = users.computeIfAbsent(id, key -> new ArrayList<>());
                    UUID user = rs.getObject("user_id", UUID.class);
                    if (user != null) {
                        list.add(user);
                    }
                }, params);
        List<RecipientSetting> found = new ArrayList<>();
        headers.forEach((id, header) -> found.add(new RecipientSetting(header.worksiteId(), header.type(),
                users.get(id), header.updatedBy(), header.updatedAt())));
        return found;
    }

    private record Header(UUID worksiteId, NotificationType type, UUID updatedBy, Instant updatedAt) {
    }
}
