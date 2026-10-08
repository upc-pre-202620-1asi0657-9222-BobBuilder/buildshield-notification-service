package pe.buildshield.notification.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.buildshield.notification.application.NotificationCommandService;
import pe.buildshield.notification.application.NotificationQueryService;
import pe.buildshield.notification.domain.model.Notification;
import pe.buildshield.notification.domain.model.NotificationType;
import pe.buildshield.notification.shared.error.ErrorResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notificaciones", description = "US43: bandeja de avisos del usuario del token")
@SecurityRequirement(name = "bearer")
class NotificationController {

    private final NotificationQueryService queries;
    private final NotificationCommandService commands;

    NotificationController(NotificationQueryService queries, NotificationCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar mis notificaciones",
            description = "Solo las del usuario del token, de la más reciente a la más antigua, con el total de no "
                    + "leídas.")
    @ApiResponse(responseCode = "200", description = "Página de la bandeja",
            content = @Content(schema = @Schema(implementation = InboxResource.class),
                    examples = @ExampleObject(value = NotificationApiExamples.INBOX)))
    InboxResource list(
            @Parameter(description = "true: solo las no leídas", example = "false")
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @Parameter(description = "Página desde 0", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página (máximo 100)", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        var inbox = queries.inbox(unreadOnly, page, size);
        return new InboxResource(inbox.page().items().stream().map(NotificationResource::of).toList(),
                inbox.unreadCount(), inbox.page().page(), inbox.page().size(), inbox.page().total());
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Contar mis notificaciones no leídas",
            description = "Para la campana de la navegación; la web la consulta cada 60 segundos.")
    @ApiResponse(responseCode = "200", description = "Cantidad de no leídas",
            content = @Content(schema = @Schema(implementation = UnreadCountResource.class),
                    examples = @ExampleObject(value = NotificationApiExamples.UNREAD_COUNT)))
    UnreadCountResource unreadCount() {
        return new UnreadCountResource(queries.unreadCount());
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Marcar una notificación como leída",
            description = "Solo una notificación propia; marcarla de nuevo no cambia la fecha de lectura.")
    @ApiResponse(responseCode = "204", description = "Marcada como leída")
    @ApiResponse(responseCode = "404", description = "No existe o es de otro usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    ResponseEntity<Void> markRead(@Parameter(description = "Id de la notificación",
            example = NotificationApiExamples.NOTIFICATION_ID) @PathVariable UUID id) {
        commands.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Marcar todas mis notificaciones como leídas", description = "Solo las del usuario del token.")
    @ApiResponse(responseCode = "204", description = "Todas marcadas como leídas")
    ResponseEntity<Void> markAllRead() {
        commands.markAllRead();
        return ResponseEntity.noContent().build();
    }

    @Schema(description = "Aviso de la bandeja")
    record NotificationResource(UUID id, NotificationType type, String title, String body,
            @Schema(description = "Ruta de la web a la que lleva el aviso") String link, Instant createdAt,
            Instant readAt) {

        static NotificationResource of(Notification notification) {
            return new NotificationResource(notification.id(), notification.type(), notification.title(),
                    notification.body(), notification.link(), notification.createdAt(), notification.readAt());
        }
    }

    @Schema(description = "Bandeja del usuario")
    record InboxResource(List<NotificationResource> items, long unreadCount, int page, int size, long total) {
    }

    @Schema(description = "Notificaciones no leídas")
    record UnreadCountResource(long unreadCount) {
    }
}
