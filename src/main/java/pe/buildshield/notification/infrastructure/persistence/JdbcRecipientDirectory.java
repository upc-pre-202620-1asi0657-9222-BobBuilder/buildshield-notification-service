package pe.buildshield.notification.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pe.buildshield.notification.domain.model.Recipient;
import pe.buildshield.notification.domain.model.RecipientDirectory;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code recipient_projection} y {@code assignment_projection}: copias de iam y organization. */
@Repository
class JdbcRecipientDirectory implements RecipientDirectory {

    private static final RowMapper<Recipient> ROW = (rs, row) -> new Recipient(rs.getObject("user_id", UUID.class),
            rs.getString("full_name"), rs.getString("email"), rs.getString("role"), rs.getBoolean("active"));

    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcRecipientDirectory(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void upsertUser(UUID organizationId, UUID userId, String fullName, String email, String role,
            boolean active) {
        jdbc.update("""
                INSERT INTO recipient_projection (user_id, organization_id, full_name, email, role, active, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                    full_name = COALESCE(EXCLUDED.full_name, recipient_projection.full_name),
                    email = COALESCE(EXCLUDED.email, recipient_projection.email),
                    role = EXCLUDED.role, active = EXCLUDED.active, updated_at = EXCLUDED.updated_at
                """, userId, organizationId, fullName, email, role, active, Timestamp.from(clock.instant()));
    }

    @Override
    public void assign(UUID organizationId, UUID assignmentId, UUID userId, UUID locationId, String locationType) {
        jdbc.update("""
                INSERT INTO assignment_projection (assignment_id, organization_id, user_id, location_id, location_type,
                    updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (assignment_id) DO UPDATE SET user_id = EXCLUDED.user_id,
                    location_id = EXCLUDED.location_id, location_type = EXCLUDED.location_type,
                    updated_at = EXCLUDED.updated_at
                """, assignmentId, organizationId, userId, locationId, locationType, Timestamp.from(clock.instant()));
    }

    @Override
    public void unassign(UUID assignmentId) {
        jdbc.update("DELETE FROM assignment_projection WHERE assignment_id = ?", assignmentId);
    }

    @Override
    public Optional<Recipient> find(UUID organizationId, UUID userId) {
        return jdbc.query("SELECT * FROM recipient_projection WHERE organization_id = ? AND user_id = ?", ROW,
                organizationId, userId).stream().findFirst();
    }

    @Override
    public List<Recipient> assignedTo(UUID organizationId, UUID locationId, String role) {
        return jdbc.query("""
                SELECT DISTINCT r.* FROM recipient_projection r
                JOIN assignment_projection a ON a.user_id = r.user_id
                WHERE r.organization_id = ? AND a.organization_id = ? AND a.location_id = ? AND r.role = ? AND r.active
                ORDER BY r.full_name NULLS LAST, r.user_id
                """, ROW, organizationId, organizationId, locationId, role);
    }

    @Override
    public List<Recipient> candidates(UUID organizationId, UUID worksiteId) {
        return jdbc.query("""
                SELECT r.* FROM recipient_projection r
                WHERE r.organization_id = ? AND r.active
                  AND (r.role <> 'SITE_MANAGER' OR EXISTS (
                        SELECT 1 FROM assignment_projection a
                        WHERE a.organization_id = r.organization_id AND a.user_id = r.user_id
                          AND a.location_id = ?))
                ORDER BY r.full_name NULLS LAST, r.user_id
                """, ROW, organizationId, worksiteId);
    }

    @Override
    public List<Recipient> withRole(UUID organizationId, String role) {
        return jdbc.query("""
                SELECT * FROM recipient_projection WHERE organization_id = ? AND role = ? AND active
                ORDER BY full_name NULLS LAST, user_id
                """, ROW, organizationId, role);
    }
}
