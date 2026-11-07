package pe.buildshield.notification.domain.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Configuración de destinatarios por obra y tipo de aviso (US45), siempre acotada a la organización. */
public interface RecipientSettingRepository {

    Optional<RecipientSetting> find(UUID organizationId, UUID worksiteId, NotificationType type);

    List<RecipientSetting> findByWorksite(UUID organizationId, UUID worksiteId);

    List<RecipientSetting> findAll(UUID organizationId);

    /** Crea o reemplaza la configuración de esa obra y ese tipo. */
    void save(UUID organizationId, RecipientSetting setting);

    /** @return {@code false} si no había configuración */
    boolean delete(UUID organizationId, UUID worksiteId, NotificationType type);
}
