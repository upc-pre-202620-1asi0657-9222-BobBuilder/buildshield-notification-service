package pe.buildshield.notification.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.buildshield.notification.application.RecipientSettingsService;
import pe.buildshield.notification.application.RecipientSettingsService.TypeSettings;
import pe.buildshield.notification.domain.model.NotificationType;
import pe.buildshield.notification.domain.model.Recipient;
import pe.buildshield.notification.shared.error.ErrorResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications/recipient-settings")
@Tag(name = "Destinatarios de notificación", description = "US45: el administrador elige, por obra, quién recibe "
        + "cada tipo de aviso")
@SecurityRequirement(name = "bearer")
@PreAuthorize("hasRole('ADMINISTRATOR')")
class RecipientSettingsController {

    private final RecipientSettingsService settings;

    RecipientSettingsController(RecipientSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    @Operation(summary = "Listar las configuraciones de destinatarios",
            description = "Solo las obras y los tipos de aviso que el administrador configuró; los demás usan la regla "
                    + "por defecto de US43. Solo el administrador.")
    @ApiResponse(responseCode = "200", description = "Configuraciones de la organización",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ConfiguredResource.class)),
                    examples = @ExampleObject(value = NotificationApiExamples.RECIPIENT_SETTINGS)))
    @ApiResponse(responseCode = "403", description = "El usuario no es administrador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    List<ConfiguredResource> list() {
        return settings.list().stream().map(configured -> new ConfiguredResource(configured.worksiteId(),
                configured.worksiteName(), TypeResource.of(configured.setting()))).toList();
    }

    @GetMapping("/worksites/{worksiteId}")
    @Operation(summary = "Consultar los destinatarios de una obra",
            description = "Los ocho tipos de aviso de la obra, cada uno configurado o con su regla por defecto, y los "
                    + "usuarios que se pueden elegir (activos; los encargados de obra, solo los asignados a la obra).")
    @ApiResponse(responseCode = "200", description = "Destinatarios de la obra",
            content = @Content(schema = @Schema(implementation = WorksiteResource.class),
                    examples = @ExampleObject(value = NotificationApiExamples.WORKSITE_SETTINGS)))
    @ApiResponse(responseCode = "404", description = "La obra no existe en la organización",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    WorksiteResource worksite(@Parameter(description = "Id de la obra", example = NotificationApiExamples.WORKSITE_ID)
            @PathVariable UUID worksiteId) {
        var view = settings.forWorksite(worksiteId);
        return new WorksiteResource(view.worksiteId(), view.worksiteName(),
                view.types().stream().map(TypeResource::of).toList(),
                view.candidates().stream().map(RecipientResource::of).toList());
    }

    @PutMapping("/worksites/{worksiteId}/types/{type}")
    @Operation(summary = "Elegir los destinatarios de un tipo de aviso en una obra",
            description = "Reemplaza a los destinatarios por rol de la regla por defecto. Una lista vacía deja el aviso "
                    + "sin destinatarios por rol en esa obra. Quien abrió una discrepancia sigue recibiendo los avisos "
                    + "de su caso y nunca se avisa a quien provocó el evento.")
    @ApiResponse(responseCode = "200", description = "Configuración guardada",
            content = @Content(schema = @Schema(implementation = TypeResource.class),
                    examples = @ExampleObject(value = NotificationApiExamples.TYPE_SETTINGS)))
    @ApiResponse(responseCode = "400", description = "Usuario inexistente o inactivo, encargado de obra no asignado a "
            + "la obra, más de 50 destinatarios o tipo desconocido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La obra no existe en la organización",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    TypeResource replace(
            @Parameter(description = "Id de la obra", example = NotificationApiExamples.WORKSITE_ID)
            @PathVariable UUID worksiteId,
            @Parameter(description = "Tipo de aviso", example = "DISPATCH_IN_TRANSIT") @PathVariable NotificationType type,
            @Valid @RequestBody RecipientsRequest request) {
        return TypeResource.of(settings.replace(worksiteId, type, request.userIds()));
    }

    @DeleteMapping("/worksites/{worksiteId}/types/{type}")
    @Operation(summary = "Volver a la regla por defecto",
            description = "Borra la configuración de ese tipo de aviso en la obra; repetirlo no falla.")
    @ApiResponse(responseCode = "204", description = "La obra usa otra vez la regla por defecto para ese aviso")
    @ApiResponse(responseCode = "404", description = "La obra no existe en la organización",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<Void> reset(
            @Parameter(description = "Id de la obra", example = NotificationApiExamples.WORKSITE_ID)
            @PathVariable UUID worksiteId,
            @Parameter(description = "Tipo de aviso", example = "DISPATCH_IN_TRANSIT") @PathVariable NotificationType type) {
        settings.reset(worksiteId, type);
        return ResponseEntity.noContent().build();
    }

    @Schema(description = "Usuarios que reciben el aviso")
    record RecipientsRequest(@NotNull @Schema(description = "Ids de usuario, sin repetidos; puede ser vacía",
            example = "[\"1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f\"]") List<UUID> userIds) {
    }

    @Schema(description = "Usuario elegible o elegido")
    record RecipientResource(UUID userId, String fullName, String email, String role,
            @Schema(description = "false: inactivo o ya no existe; no recibe avisos") boolean active) {

        static RecipientResource of(Recipient recipient) {
            return new RecipientResource(recipient.userId(), recipient.fullName(), recipient.email(),
                    recipient.role(), recipient.active());
        }
    }

    @Schema(description = "Destinatarios de un tipo de aviso")
    record TypeResource(NotificationType type, String label,
            @Schema(description = "false: se aplica defaultAudience") boolean configured, String defaultAudience,
            List<RecipientResource> recipients, UUID updatedBy, Instant updatedAt) {

        static TypeResource of(TypeSettings settings) {
            return new TypeResource(settings.type(), settings.label(), settings.configured(),
                    settings.defaultAudience(), settings.recipients().stream().map(RecipientResource::of).toList(),
                    settings.updatedBy(), settings.updatedAt());
        }
    }

    @Schema(description = "Destinatarios de los ocho tipos de aviso en una obra")
    record WorksiteResource(UUID worksiteId, String worksiteName, List<TypeResource> types,
            List<RecipientResource> candidates) {
    }

    @Schema(description = "Configuración de un tipo de aviso en una obra")
    record ConfiguredResource(UUID worksiteId, String worksiteName, TypeResource setting) {
    }
}
