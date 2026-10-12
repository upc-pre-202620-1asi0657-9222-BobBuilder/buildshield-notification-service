package pe.buildshield.notification.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.buildshield.notification.domain.model.DispatchInfo;
import pe.buildshield.notification.domain.model.LogisticsDirectory;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/** {@code location_projection} y {@code dispatch_projection}: nombres de lugares y datos de despachos. */
@Repository
class JdbcLogisticsDirectory implements LogisticsDirectory {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcLogisticsDirectory(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void upsertLocation(UUID organizationId, UUID locationId, String type, String name) {
        jdbc.update("""
                INSERT INTO location_projection (location_id, organization_id, type, name, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (location_id) DO UPDATE SET type = EXCLUDED.type, name = EXCLUDED.name,
                    updated_at = EXCLUDED.updated_at
                """, locationId, organizationId, type, name, Timestamp.from(clock.instant()));
    }

    @Override
    public Optional<String> locationName(UUID organizationId, UUID locationId) {
        return jdbc.query("SELECT name FROM location_projection WHERE organization_id = ? AND location_id = ?",
                (rs, row) -> rs.getString(1), organizationId, locationId).stream().findFirst();
    }

    @Override
    public boolean isWorksite(UUID organizationId, UUID locationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM location_projection
                               WHERE organization_id = ? AND location_id = ? AND type = 'WORKSITE')
                """, Boolean.class, organizationId, locationId));
    }

    @Override
    public void upsertDispatch(UUID organizationId, UUID dispatchId, String manifestCode, UUID worksiteId,
            UUID warehouseId) {
        jdbc.update("""
                INSERT INTO dispatch_projection (dispatch_id, organization_id, manifest_code, worksite_id, warehouse_id,
                    updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (dispatch_id) DO UPDATE SET
                    manifest_code = COALESCE(EXCLUDED.manifest_code, dispatch_projection.manifest_code),
                    worksite_id = COALESCE(EXCLUDED.worksite_id, dispatch_projection.worksite_id),
                    warehouse_id = COALESCE(EXCLUDED.warehouse_id, dispatch_projection.warehouse_id),
                    updated_at = EXCLUDED.updated_at
                """, dispatchId, organizationId, manifestCode, worksiteId, warehouseId, Timestamp.from(clock.instant()));
    }

    @Override
    public Optional<DispatchInfo> dispatch(UUID organizationId, UUID dispatchId) {
        return jdbc.query("""
                SELECT dispatch_id, manifest_code, worksite_id, warehouse_id FROM dispatch_projection
                WHERE organization_id = ? AND dispatch_id = ?
                """, (rs, row) -> new DispatchInfo(rs.getObject("dispatch_id", UUID.class), rs.getString("manifest_code"),
                rs.getObject("worksite_id", UUID.class), rs.getObject("warehouse_id", UUID.class)), organizationId,
                dispatchId).stream().findFirst();
    }
}
