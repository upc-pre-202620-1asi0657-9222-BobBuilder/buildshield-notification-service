package pe.buildshield.notification.domain.model;

import java.util.Optional;
import java.util.UUID;

/** Copias locales de obras, almacenes y despachos, solo para redactar los avisos. */
public interface LogisticsDirectory {

    void upsertLocation(UUID organizationId, UUID locationId, String type, String name);

    Optional<String> locationName(UUID organizationId, UUID locationId);

    /** Completa los datos conocidos del despacho; los nulos no borran lo que ya se sabía. */
    void upsertDispatch(UUID organizationId, UUID dispatchId, String manifestCode, UUID worksiteId, UUID warehouseId);

    Optional<DispatchInfo> dispatch(UUID organizationId, UUID dispatchId);
}
