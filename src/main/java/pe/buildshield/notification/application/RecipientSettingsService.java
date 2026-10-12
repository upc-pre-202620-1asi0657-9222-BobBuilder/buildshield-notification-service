package pe.buildshield.notification.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.buildshield.notification.domain.model.LogisticsDirectory;
import pe.buildshield.notification.domain.model.NotificationType;
import pe.buildshield.notification.domain.model.Recipient;
import pe.buildshield.notification.domain.model.RecipientDirectory;
import pe.buildshield.notification.domain.model.RecipientSetting;
import pe.buildshield.notification.domain.model.RecipientSettingRepository;
import pe.buildshield.notification.shared.error.ErrorDetail;
import pe.buildshield.notification.shared.error.ResourceNotFoundException;
import pe.buildshield.notification.shared.error.ValidationException;
import pe.buildshield.notification.shared.tenant.TenantContext;
import pe.buildshield.notification.shared.tenant.TenantInfo;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Configuración de destinatarios de notificación por obra y tipo de aviso (US45). Solo el administrador la usa (lo
 * exige el controlador). La obra y los usuarios salen de las copias locales que alimentan los eventos de organization
 * e iam: no se llama a otros servicios.
 *
 * <p>Un usuario se puede elegir si está activo en la organización; un encargado de obra, además, debe estar asignado a
 * esa obra (si no, el enlace del aviso lo llevaría a una obra que no puede ver). Al enviar se vuelve a comprobar: si
 * después lo desactivan o lo desasignan, deja de recibir.
 */
@Service
public class RecipientSettingsService {

    private final RecipientSettingRepository settings;
    private final RecipientDirectory recipients;
    private final LogisticsDirectory logistics;
    private final Clock clock;

    public RecipientSettingsService(RecipientSettingRepository settings, RecipientDirectory recipients,
            LogisticsDirectory logistics, Clock clock) {
        this.settings = settings;
        this.recipients = recipients;
        this.logistics = logistics;
        this.clock = clock;
    }

    /** Los ocho tipos de aviso de la obra: configurados o con la regla por defecto, y quiénes se pueden elegir. */
    @Transactional(readOnly = true)
    public WorksiteSettings forWorksite(UUID worksiteId) {
        UUID organizationId = TenantContext.require().organizationId();
        String worksiteName = requireWorksite(organizationId, worksiteId);
        Map<NotificationType, RecipientSetting> configured = settings.findByWorksite(organizationId, worksiteId)
                .stream().collect(Collectors.toMap(RecipientSetting::type, Function.identity()));
        Map<UUID, Recipient> known = known(organizationId, configured.values().stream()
                .flatMap(setting -> setting.userIds().stream()).toList());
        List<TypeSettings> types = Arrays.stream(NotificationType.values())
                .map(type -> typeSettings(type, Optional.ofNullable(configured.get(type)), known)).toList();
        return new WorksiteSettings(worksiteId, worksiteName, types,
                recipients.candidates(organizationId, worksiteId));
    }

    /** Todas las configuraciones de la organización, por obra; lo que no aparece usa la regla por defecto. */
    @Transactional(readOnly = true)
    public List<ConfiguredSetting> list() {
        UUID organizationId = TenantContext.require().organizationId();
        List<RecipientSetting> all = settings.findAll(organizationId);
        Map<UUID, Recipient> known = known(organizationId, all.stream()
                .flatMap(setting -> setting.userIds().stream()).toList());
        return all.stream().map(setting -> new ConfiguredSetting(setting.worksiteId(),
                logistics.locationName(organizationId, setting.worksiteId()).orElse(null),
                typeSettings(setting.type(), Optional.of(setting), known))).toList();
    }

    /** Reemplaza los destinatarios de ese tipo de aviso en la obra. Una lista vacía silencia el aviso por rol. */
    @Transactional
    public TypeSettings replace(UUID worksiteId, NotificationType type, List<UUID> userIds) {
        TenantInfo requester = TenantContext.require();
        UUID organizationId = requester.organizationId();
        requireWorksite(organizationId, worksiteId);
        List<UUID> chosen = new ArrayList<>(new LinkedHashSet<>(userIds == null ? List.<UUID>of() : userIds));
        if (chosen.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ValidationException("INVALID_RECIPIENTS", "La lista de destinatarios tiene valores vacíos");
        }
        if (chosen.size() > RecipientSetting.MAX_RECIPIENTS) {
            throw new ValidationException("TOO_MANY_RECIPIENTS", "Se pueden elegir hasta "
                    + RecipientSetting.MAX_RECIPIENTS + " destinatarios por obra y tipo de aviso");
        }
        Map<UUID, Recipient> eligible = recipients.candidates(organizationId, worksiteId).stream()
                .collect(Collectors.toMap(Recipient::userId, Function.identity()));
        List<ErrorDetail> unknown = new ArrayList<>();
        List<ErrorDetail> unassigned = new ArrayList<>();
        for (UUID userId : chosen) {
            if (eligible.containsKey(userId)) {
                continue;
            }
            Optional<Recipient> user = recipients.find(organizationId, userId).filter(Recipient::active);
            if (user.isEmpty()) {
                unknown.add(new ErrorDetail("userIds", userId + " no es un usuario activo de la organización"));
            } else {
                unassigned.add(new ErrorDetail("userIds", userId + " es encargado de obra y no está asignado a "
                        + "esta obra"));
            }
        }
        if (!unknown.isEmpty()) {
            throw new ValidationException("UNKNOWN_RECIPIENT", "Hay destinatarios que no son usuarios activos",
                    unknown);
        }
        if (!unassigned.isEmpty()) {
            throw new ValidationException("RECIPIENT_NOT_ASSIGNED",
                    "Un encargado de obra solo recibe avisos de las obras que tiene asignadas", unassigned);
        }
        RecipientSetting setting = new RecipientSetting(worksiteId, type, chosen, requester.userId(),
                clock.instant());
        settings.save(organizationId, setting);
        return typeSettings(type, Optional.of(setting), eligible);
    }

    /** Vuelve a la regla por defecto. Repetirlo no falla. */
    @Transactional
    public void reset(UUID worksiteId, NotificationType type) {
        UUID organizationId = TenantContext.require().organizationId();
        requireWorksite(organizationId, worksiteId);
        settings.delete(organizationId, worksiteId, type);
    }

    private String requireWorksite(UUID organizationId, UUID worksiteId) {
        if (!logistics.isWorksite(organizationId, worksiteId)) {
            throw new ResourceNotFoundException("WORKSITE_NOT_FOUND", "La obra " + worksiteId + " no existe");
        }
        return logistics.locationName(organizationId, worksiteId).orElse(null);
    }

    private Map<UUID, Recipient> known(UUID organizationId, Collection<UUID> userIds) {
        return userIds.stream().distinct().map(id -> recipients.find(organizationId, id)).flatMap(Optional::stream)
                .collect(Collectors.toMap(Recipient::userId, Function.identity()));
    }

    private static TypeSettings typeSettings(NotificationType type, Optional<RecipientSetting> setting,
            Map<UUID, Recipient> known) {
        if (setting.isEmpty()) {
            return new TypeSettings(type, type.label(), false, type.defaultAudience(), List.of(), null, null);
        }
        List<Recipient> chosen = setting.get().userIds().stream()
                .map(id -> known.getOrDefault(id, new Recipient(id, null, null, null, false))).toList();
        return new TypeSettings(type, type.label(), true, type.defaultAudience(), chosen,
                setting.get().updatedBy(), setting.get().updatedAt());
    }

    /**
     * @param configured {@code false}: se aplica {@code defaultAudience}
     * @param recipients los elegidos; uno desconocido o inactivo aparece con {@code active = false} y no recibe
     */
    public record TypeSettings(NotificationType type, String label, boolean configured, String defaultAudience,
            List<Recipient> recipients, UUID updatedBy, Instant updatedAt) {
    }

    public record WorksiteSettings(UUID worksiteId, String worksiteName, List<TypeSettings> types,
            List<Recipient> candidates) {
    }

    public record ConfiguredSetting(UUID worksiteId, String worksiteName, TypeSettings setting) {
    }
}
