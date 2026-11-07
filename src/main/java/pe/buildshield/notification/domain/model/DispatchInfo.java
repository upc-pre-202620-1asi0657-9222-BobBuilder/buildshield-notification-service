package pe.buildshield.notification.domain.model;

import java.util.UUID;

/** Lo que notification sabe de un despacho por los eventos de ordering. Los campos pueden ser nulos. */
public record DispatchInfo(UUID dispatchId, String manifestCode, UUID worksiteId, UUID warehouseId) {

    /** El código del manifiesto o, si todavía no llegó, una referencia corta al despacho. */
    public String reference() {
        if (manifestCode != null && !manifestCode.isBlank()) {
            return manifestCode;
        }
        return dispatchId.toString().substring(0, 8);
    }
}
