package pe.buildshield.notification.application;

import pe.buildshield.notification.domain.model.NotificationType;
import pe.buildshield.notification.domain.model.RecipientSetting;
import pe.buildshield.notification.domain.model.RecipientSettingRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Configuración de destinatarios en memoria, por organización, obra y tipo. */
class InMemoryRecipientSettings implements RecipientSettingRepository {

    private record Key(UUID organizationId, UUID worksiteId, NotificationType type) {
    }

    final Map<Key, RecipientSetting> settings = new LinkedHashMap<>();

    @Override
    public Optional<RecipientSetting> find(UUID organizationId, UUID worksiteId, NotificationType type) {
        return Optional.ofNullable(settings.get(new Key(organizationId, worksiteId, type)));
    }

    @Override
    public List<RecipientSetting> findByWorksite(UUID organizationId, UUID worksiteId) {
        return settings.entrySet().stream().filter(e -> e.getKey().organizationId().equals(organizationId)
                && e.getKey().worksiteId().equals(worksiteId)).map(Map.Entry::getValue).toList();
    }

    @Override
    public List<RecipientSetting> findAll(UUID organizationId) {
        return settings.entrySet().stream().filter(e -> e.getKey().organizationId().equals(organizationId))
                .map(Map.Entry::getValue).toList();
    }

    @Override
    public void save(UUID organizationId, RecipientSetting setting) {
        settings.put(new Key(organizationId, setting.worksiteId(), setting.type()), setting);
    }

    @Override
    public boolean delete(UUID organizationId, UUID worksiteId, NotificationType type) {
        return Objects.nonNull(settings.remove(new Key(organizationId, worksiteId, type)));
    }
}
