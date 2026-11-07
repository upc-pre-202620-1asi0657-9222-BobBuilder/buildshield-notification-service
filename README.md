# buildshield-notification-service

Notificaciones de BuildShield (BobBuilder): bandeja de avisos en la aplicación y envío de correo, alimentados exclusivamente por eventos de integración de los demás contextos (US43), con destinatarios configurables por obra (US45).

Alineado con la arquitectura v4 (`buildshield-presentacion-v4.pdf`, ER pág. 18 y C4 pág. 29).

| Dato | Valor |
|---|---|
| Puerto | 8087 |
| Base | `notification_db` (PostgreSQL 16+) |
| Paquete | `pe.buildshield.notification` (kernel compartido en `pe.buildshield.notification.shared`) |
| Publica | Nada (sin outbox ni tópico propio, solo consume) |
| Consumidor | `NotificationEventConsumer` (tópicos: `buildshield.iam`, `buildshield.organization`, `buildshield.ordering`, `buildshield.inventory`, `buildshield.discrepancy`, `buildshield.subscription`) |
| Dominio | Agregados `Notification` y `NotificationTemplate` (`Plantilla`) |
| Correo | `SendGridAdapter` con Circuit Breaker Resilience4j (no bloqueante en fallas o circuito abierto) |

## Eventos que consume

| Tópico | Eventos consumidos | Destinatarios por defecto | `link` |
|---|---|---|---|
| `buildshield.ordering` | `DispatchDeparted` | Encargados de obra asignados a la obra destino (`SITE_MANAGER`) | `/reception/{dispatchId}` |
| `buildshield.ordering` | `DispatchCancelled` | Encargados de obra de la obra destino | `/dispatches/{dispatchId}/custody` |
| `buildshield.ordering` | `TransitIncidentReported` | Encargados de obra de la obra destino y administradores | `/dispatches/{dispatchId}` (`/custody` para encargados de obra) |
| `buildshield.ordering` | `ReceptionConfirmed` (con líneas fuera de tolerancia) | Encargados de almacén del origen y administradores | `/dispatches/{dispatchId}` |
| `buildshield.ordering` | `OrderApproved` | Encargados de obra de la obra y encargados de almacén | `/orders/{orderId}` |
| `buildshield.inventory` | `StockReservationRejected` | Encargados de obra de la obra y administradores | `/orders/{orderId}` |
| `buildshield.discrepancy` | `DiscrepancyOpened` | Responsables de la obra (`SITE_MANAGER` asignados) | `/discrepancies/{discrepancyId}` |
| `buildshield.discrepancy` | `DiscrepancyResponded` | Quien abrió la discrepancia (`openedBy`) | `/discrepancies/{discrepancyId}` |
| `buildshield.discrepancy` | `DiscrepancyEscalated` | Administradores (`ADMINISTRATOR`) | `/discrepancies/{discrepancyId}` |
| `buildshield.discrepancy` | `DiscrepancyResolved`, `DiscrepancyAccepted` | Encargados de almacén del origen y quien abrió la discrepancia | `/discrepancies/{discrepancyId}` |
| `buildshield.subscription` | `PaymentSucceeded`, `PaymentFailed`, `SubscriptionExpired` | Administradores (`ADMINISTRATOR`) | `/subscription` |
| `buildshield.iam` | `PasswordResetRequested` | Correo de recuperación enviado al destinatario vía `EmailPort` | Enlace de restablecimiento |
| `buildshield.iam` | `UserCreated`, `UserUpdated` | Actualiza copia local de destinatarios (`recipient_projection`) | N/A |
| `buildshield.organization` | `LocationRegistered`, `LocationUpdated` | Actualiza copia local de obras y almacenes (`location_projection`) | N/A |
| `buildshield.organization` | `StaffAssigned`, `StaffUnassigned` | Actualiza asignaciones de personal (`assignment_projection`) | N/A |

### Reglas de negocio de avisos
- Los enlaces son rutas web. El detalle `/dispatches/{id}` se ajusta a `/dispatches/{id}/custody` para el encargado de obra.
- Nunca se avisa a quien provocó el evento (salvo eventos de alerta general de obra como `DiscrepancyOpened` donde se asegura la notificación a los responsables de la obra).
- Los eventos duplicados no duplican avisos gracias al consumidor idempotente `NotificationEventConsumer` con la tabla `processed_events (event_id, consumer)`.
- **US45:** Si el administrador configuró destinatarios para la obra y el tipo de aviso (`notification_preferences`), esos usuarios reemplazan a la regla por defecto.
- **Resiliencia de correo:** El envío a SendGrid está protegido por `CircuitBreaker`. Si SendGrid falla o el circuito está abierto (`CallNotPermittedException`), la operación **no se bloquea**, la notificación se conserva en estado `PENDING` para reintento posterior (`retryPending()`), o en `FAILED` registrando el intento.

## Endpoints REST

### Bandeja de notificaciones (US43)
- `GET /api/v1/notifications?unreadOnly=false&page=0&size=20` → Bandeja paginada del usuario autenticado.
- `GET /api/v1/notifications/unread-count` → Total de avisos no leídos.
- `POST /api/v1/notifications/{id}/read` → Marca una notificación como leída (404 si pertenece a otro usuario).
- `POST /api/v1/notifications/read-all` → Marca todas como leídas.

### Destinatarios por obra (US45, rol ADMINISTRATOR)
- `GET /api/v1/notifications/recipient-settings` → Configuraciones guardadas por obra.
- `GET /api/v1/notifications/recipient-settings/worksites/{worksiteId}` → Tipos de aviso y candidatos asignables.
- `PUT /api/v1/notifications/recipient-settings/worksites/{worksiteId}/types/{type}` → Guarda los usuarios asignados.
- `DELETE /api/v1/notifications/recipient-settings/worksites/{worksiteId}/types/{type}` → Restaura la regla por defecto.

OpenAPI disponible en `/api/v1/api-docs` y `/swagger-ui.html`.

## Ejecución de Pruebas

```bash
# Pruebas unitarias y de arquitectura (ArchUnit)
.\mvnw.cmd test

# Pruebas completas de integración (*IT), Cucumber y JaCoCo con base externa
.\mvnw.cmd verify -Dbuildshield.test.external-database=true
```
