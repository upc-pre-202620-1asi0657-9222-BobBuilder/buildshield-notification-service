package pe.buildshield.notification.domain.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Copias locales de usuarios y asignaciones: a quién avisar de cada evento. Solo devuelve usuarios activos. */
public interface RecipientDirectory {

    void upsertUser(UUID organizationId, UUID userId, String fullName, String email, String role, boolean active);

    void assign(UUID organizationId, UUID assignmentId, UUID userId, UUID locationId, String locationType);

    void unassign(UUID assignmentId);

    Optional<Recipient> find(UUID organizationId, UUID userId);

    /** Usuarios activos con ese rol asignados a la obra o almacén. */
    List<Recipient> assignedTo(UUID organizationId, UUID locationId, String role);

    List<Recipient> withRole(UUID organizationId, String role);

    /**
     * Quienes pueden recibir avisos de la obra (US45): usuarios activos de la organización, salvo encargados de obra
     * que no estén asignados a esa obra.
     */
    List<Recipient> candidates(UUID organizationId, UUID worksiteId);
}
