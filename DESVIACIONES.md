# Desviaciones frente al ER de notification_db

Fuente fija: sección `notification_db` de `wiki/informe-avance2/er-corregido.md`. Este archivo lista lo que el servicio tiene y el ER no dibuja, con su motivo, para que se agregue al ER del informe o se retire.

## Tablas fuera del ER

| Tabla | Motivo |
|---|---|
| `notification_preference_overrides` | Marca que el administrador configuró destinatarios para una obra y un tipo de aviso. Hace falta porque una lista vacía ("nadie por rol") no se puede representar con filas de `notification_preferences`: sin esta tabla, esa configuración se perdería y volvería la regla por defecto. Guarda también `updated_by` y `updated_at`, que el ER no tiene. PK `(organization_id, location_id, type)`. |

## Columnas fuera del ER

| Tabla | Columna | Motivo |
|---|---|---|
| `notification_preferences` | `position` | Orden de los destinatarios elegido por el administrador (antes `recipient_setting_users.position`). Define el orden en que se crean los avisos y lo que muestra la pantalla de configuración. |

## Cambios de modelo respecto del esquema anterior

- `notifications.source_event_id` y su índice único `(source_event_id, recipient_user_id)` se retiran. Evitar avisos repetidos ahora lo cubre `processed_events` con PK `(event_id, consumer)`: el consumidor registra el evento y crea los avisos en la misma transacción, por lo que un evento entregado dos veces no se procesa de nuevo. La migración V3 copia cada evento ya procesado a los cuatro consumidores para que una reentrega posterior a la migración también se ignore.
- Un aviso ya no puede enlazarse al evento que lo originó. Es información de trazabilidad que el ER no guarda.
- `title`, `body` y `link` viven en `payload` (jsonb). La API REST sigue devolviendo `title`, `body` y `link`, armados desde `payload`. Los límites de 150 y 500 caracteres se aplican en el dominio, ya no en columnas.
- `notifications.type` baja de `varchar(40)` a `varchar(30)` (el nombre más largo mide 28).
- Avisos anteriores a V3: se migran con `status = SENT`, `attempts = 1` y `sent_at = created_at`, porque el correo se enviaba al crear el aviso; no hay registro del resultado real.
- Avisos para destinatarios sin correo conocido (por ejemplo, quien abrió una discrepancia y aún no está en `recipient_projection`) quedan en `PENDING` con 0 intentos: no hay envío que registrar.
- El ER no define reintentos: `status`, `attempts` y `sent_at` solo registran el resultado del envío inmediato después del commit; no hay un proceso que reintente los `FAILED`.
