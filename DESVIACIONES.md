# Desviaciones frente a la Arquitectura v4 (`notification_db`)

Fuente fija: Presentación **`buildshield-presentacion-v4.pdf`** (BobBuilder, 1ASI0657, 5 de octubre de 2026), específicamente el Diagrama Entidad-Relación de `notification_db` (pág. 18) y el Diagrama de Componentes C4 de Notification Service (pág. 29).

Este documento registra los componentes, tablas y columnas adicionales implementados en el servicio frente al diseño estricto de la presentación v4, junto con su justificación funcional y técnica.

---

## 1. Componentes adicionales frente al diagrama C4 (pág. 29)

El C4 de la página 29 define los siguientes componentes oficiales:
- `NotificationCommandService`: casos de uso de creación y envío de avisos según eventos.
- `Notification Domain`: agregados `Notification` y `Plantilla` (`NotificationTemplate`).
- `NotificationRepository`: persistencia del historial de avisos.
- `SendGridAdapter`: adaptador de envío de correos con Circuit Breaker.
- `NotificationEventConsumer`: consumidor unificado de tópicos Kafka (`iam`, `organization`, `ordering`, `inventory`, `discrepancy`, `subscription`).

### Componentes extra implementados:

| Componente | Paquete | Justificación |
|---|---|---|
| `NotificationController` | `pe.buildshield.notification.interfaces.rest` | Expone los endpoints REST bajo `/api/v1/notifications` para listar la bandeja del usuario (`inbox`), consultar el conteo de no leídas (`unread-count`) y marcar avisos como leídos (`markRead`, `markAllRead`). Aunque omitido en el diagrama C4, la columna `read_at` del ER (pág. 18) y la interfaz web exigen estos casos de uso. |
| `NotificationQueryService` | `pe.buildshield.notification.application` | Servicio de aplicación de solo lectura que consulta la bandeja del usuario autenticado de forma paginada y calcula el conteo de no leídas respetando el aislamiento multiempresa (`TenantContext`). |
| `RecipientSettingsController` | `pe.buildshield.notification.interfaces.rest` | Expone los endpoints REST bajo `/api/v1/notifications/recipient-settings` para la configuración de destinatarios por obra y tipo de aviso (requisito US45), reservado al rol `ADMINISTRATOR`. |
| `RecipientSettingsService` | `pe.buildshield.notification.application` | Servicio de aplicación para consultar y actualizar la lista de destinatarios preferidos por obra y tipo de notificación (US45). |

---

## 2. Tablas adicionales frente al Diagrama ER (pág. 18)

El ER de la página 18 define únicamente: `notifications`, `notification_preferences` y `processed_events`.

### Tablas extra implementadas (proyecciones locales del patrón CQRS):

| Tabla | Justificación |
|---|---|
| `recipient_projection` | Proyección local de usuarios sincronizada desde eventos de IAM (`UserCreated`, `UserUpdated`). En la arquitectura v4 no hay llamadas HTTP entre microservicios (RNF18, A.2). Permite validar existencia, rol, vigencia (`active`) y correo electrónico de los destinatarios sin acoplarse sincrónicamente a IAM. |
| `assignment_projection` | Proyección local de asignaciones de personal sincronizada desde Organization (`StaffAssigned`, `StaffUnassigned`). Permite determinar rápidamente qué encargados de obra (`SITE_MANAGER`) y de almacén (`WAREHOUSE_MANAGER`) están asignados a cada ubicación sin llamar a Organization. |
| `location_projection` | Proyección local de obras y almacenes sincronizada desde Organization (`LocationRegistered`, `LocationUpdated`). Permite incluir los nombres legibles de obras y almacenes en los avisos ("Torre Norte", "Almacén Central") en lugar de mostrar únicamente UUIDs. |
| `dispatch_projection` | Proyección local de despachos (`manifest_code`, `worksite_id`, `warehouse_id`) construida a partir de eventos de Ordering (`DispatchDeparted`, `DispatchCancelled`, etc.). Necesaria porque eventos como `TransitIncidentReported` únicamente traen el `dispatchId` y carecen del `worksiteId` en su payload raíz. |
| `notification_preference_overrides` | Tabla auxiliar que registra explícitamente qué combinaciones `(organization_id, location_id, type)` han sido configuradas por el administrador. Permite distinguir entre una obra sin configuración (que debe aplicar la regla por defecto de US43) y una obra configurada intencionalmente con 0 destinatarios ("nadie por rol"). Además registra auditoría (`updated_by`, `updated_at`). |

---

## 3. Columnas y diferencias de esquema frente al Diagrama ER (pág. 18)

| Tabla | Columna / Diferencia | Justificación |
|---|---|---|
| `notification_preferences` | `position` | Representa el orden de prioridad de los destinatarios definido por el administrador en US45 (antes `recipient_setting_users.position`). |
| `notification_preferences` | Ausencia de `email` directo | El ER (pág. 18) incluye `email varchar(120)` en `notification_preferences`. En el modelo implementado, el email vive en `recipient_projection` referenciado por `user_id`. Esto evita duplicar la dirección de correo en múltiples filas de preferencias y garantiza que la actualización de correo (`UserUpdated`) se refleje de inmediato en todos los avisos. |
| `notifications` | `payload jsonb` | El ER almacena `payload jsonb` conteniendo `title`, `body` y `link`. El modelo de dominio (`Notification`) deserializa estos campos y los expone fuertemente tipados a través de la API REST. |
