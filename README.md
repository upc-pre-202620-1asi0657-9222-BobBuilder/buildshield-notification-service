# buildshield-notification-service

Notificaciones de BuildShield (BobBuilder): bandeja de avisos en la aplicación y correo, alimentados solo por eventos
de los demás contextos (US43). Contrato de la arquitectura: `wiki/informe-tf/plan-microservicios.md` del proyecto.

| Dato | Valor |
|---|---|
| Puerto | 8087 |
| Base | `notification_db` (PostgreSQL 16+) |
| Paquete | `pe.buildshield.notification` (kernel compartido copiado de iam en `pe.buildshield.notification.shared`) |
| Publica | nada (sin outbox ni tópico propio) |
| Consume | `buildshield.iam` (`UserCreated`, `UserUpdated` → `recipient_projection`), `buildshield.organization` (`LocationRegistered`, `LocationUpdated` → `location_projection`; `StaffAssigned`, `StaffUnassigned` → `assignment_projection`), `buildshield.ordering` (`DispatchDeparted`, `DispatchCancelled`, `TransitIncidentReported`, `ReceptionConfirmed`; todo evento con `dispatchId` completa `dispatch_projection`) y `buildshield.discrepancy` (`DiscrepancyResponded`, `DiscrepancyEscalated`, `DiscrepancyResolved`, `DiscrepancyAccepted`) |

## A quién se avisa

| Evento | Destinatarios | `link` |
|---|---|---|
| `DispatchDeparted` (despacho en tránsito) | encargados de obra asignados a la obra destino | `/reception/{dispatchId}` |
| `DispatchCancelled` | encargados de obra de la obra destino | `/dispatches/{dispatchId}/custody` |
| `TransitIncidentReported` | encargados de obra de la obra destino y administradores | `/dispatches/{dispatchId}` (`/custody` para el encargado de obra) |
| `ReceptionConfirmed` con alguna línea `withinTolerance = false` | encargados de almacén del almacén de origen y administradores | `/dispatches/{dispatchId}` |
| `DiscrepancyResponded` | quien la abrió (`openedBy`) | `/discrepancies/{id}` |
| `DiscrepancyEscalated` | administradores | `/discrepancies/{id}` |
| `DiscrepancyResolved`, `DiscrepancyAccepted` | encargados de almacén del almacén de origen y quien la abrió | `/discrepancies/{id}` |

- Los enlaces son rutas de la web tal cual. El detalle `/dispatches/{id}` no es para el encargado de obra: a él se le
  envía `/dispatches/{id}/custody`.
- Nunca se avisa a quien provocó el evento ni a usuarios desactivados; un evento repetido no duplica avisos
  (`processed_events` y `source_event_id` único por destinatario).
- Cada aviso se envía también por correo con `SendGridAdapter` después del commit; en local y en pruebas solo se
  registra en el log (`NOTIFICATION_EMAIL_ENABLED=false`).

## Endpoints (solo las notificaciones del usuario del token, cualquier rol)

- `GET /api/v1/notifications?unreadOnly=false&page=0&size=20` →
  `{"items":[{"id","type","title","body","link","createdAt","readAt"}],"unreadCount","page","size","total"}`
- `GET /api/v1/notifications/unread-count` → `{"unreadCount"}`
- `POST /api/v1/notifications/{id}/read` → 204 (404 si no existe o es de otro usuario)
- `POST /api/v1/notifications/read-all` → 204
- OpenAPI en `/api/v1/api-docs` y `/swagger-ui.html` (perfiles local y test).

## Variables de entorno

`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS` (por defecto `localhost:9092`),
`BUILDSHIELD_JWT_PUBLIC_KEY`, `NOTIFICATION_EMAIL_ENABLED` y `NOTIFICATION_EMAIL_FROM`. Perfiles `local`, `test` y
`prod`.

## Ejecutar en local (clúster PostgreSQL 127.0.0.1:5433 con autenticación trust)

```bash
mvn -B package -DskipTests
BUILDSHIELD_JWT_PUBLIC_KEY="$(cat ~/.buildshield/jwt-public.pem)" \
java -jar target/buildshield-notification-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

## Pruebas

```bash
mvn -B verify -Dbuildshield.test.external-database=true
```

Las `*IT` usan la base temporal `notification_it` en `127.0.0.1:5433` con el usuario `buildshield_core` y Kafka
embebido con los cuatro tópicos que consume. Cucumber cubre US43. JaCoCo exige 80 % de líneas en `domain` y
`application`.
