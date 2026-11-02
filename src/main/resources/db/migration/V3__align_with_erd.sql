-- Alineación con notification_db de er-corregido.md. Conserva los datos existentes.

-- processed_events: la llave pasa a (event_id, consumer). Los eventos ya procesados se registran para los cuatro
-- consumidores, así una reentrega posterior no vuelve a crear avisos (antes lo evitaba source_event_id).
ALTER TABLE processed_events ADD COLUMN consumer VARCHAR(60);
ALTER TABLE processed_events DROP CONSTRAINT processed_events_pkey;
INSERT INTO processed_events (event_id, consumer, processed_at)
SELECT p.event_id, c.consumer, p.processed_at
FROM processed_events p
CROSS JOIN (VALUES ('IamEventConsumer'), ('OrganizationEventConsumer'), ('OrderingEventConsumer'),
                   ('DiscrepancyEventConsumer')) AS c (consumer)
WHERE p.consumer IS NULL;
DELETE FROM processed_events WHERE consumer IS NULL;
ALTER TABLE processed_events ALTER COLUMN consumer SET NOT NULL;
ALTER TABLE processed_events ADD PRIMARY KEY (event_id, consumer);

-- notifications: user_id, payload jsonb, status, attempts y sent_at. Lo anterior (title, body, link) pasa a payload.
-- Los avisos ya existentes se marcan SENT con un intento y sent_at = created_at (el envío era inmediato).
ALTER TABLE notifications ADD COLUMN user_id UUID;
ALTER TABLE notifications ADD COLUMN payload JSONB;
ALTER TABLE notifications ADD COLUMN status VARCHAR(10);
ALTER TABLE notifications ADD COLUMN attempts INT;
ALTER TABLE notifications ADD COLUMN sent_at TIMESTAMPTZ;
UPDATE notifications
SET user_id = recipient_user_id,
    payload = jsonb_build_object('title', title, 'body', body, 'link', link),
    status = 'SENT',
    attempts = 1,
    sent_at = created_at;
ALTER TABLE notifications ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN payload SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN status SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN attempts SET NOT NULL;

DROP INDEX idx_notifications_recipient;
DROP INDEX idx_notifications_unread;
DROP INDEX uk_notifications_event_recipient;
ALTER TABLE notifications DROP COLUMN recipient_user_id;
ALTER TABLE notifications DROP COLUMN title;
ALTER TABLE notifications DROP COLUMN body;
ALTER TABLE notifications DROP COLUMN link;
ALTER TABLE notifications DROP COLUMN source_event_id;
ALTER TABLE notifications ALTER COLUMN type TYPE VARCHAR(30);

CREATE INDEX idx_notifications_user
    ON notifications (organization_id, user_id, created_at DESC, id DESC);
CREATE INDEX idx_notifications_unread
    ON notifications (organization_id, user_id) WHERE read_at IS NULL;

-- notification_preferences: una fila por obra, tipo y destinatario (antes recipient_settings + recipient_setting_users).
-- position conserva el orden elegido por el administrador (desviación documentada).
CREATE TABLE notification_preferences (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    location_id     UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    type            VARCHAR(30) NOT NULL,
    position        INT         NOT NULL,
    CONSTRAINT uk_notification_preferences UNIQUE (organization_id, location_id, type, user_id)
);

-- Existencia de la configuración por obra y tipo: una lista vacía ("nadie por rol") no cabe como filas de
-- notification_preferences. Tabla fuera del ER, documentada en DESVIACIONES.md.
CREATE TABLE notification_preference_overrides (
    organization_id UUID        NOT NULL,
    location_id     UUID        NOT NULL,
    type            VARCHAR(30) NOT NULL,
    updated_by      UUID        NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (organization_id, location_id, type)
);

INSERT INTO notification_preference_overrides (organization_id, location_id, type, updated_by, updated_at)
SELECT organization_id, worksite_id, notification_type, updated_by, updated_at FROM recipient_settings;

INSERT INTO notification_preferences (id, organization_id, location_id, user_id, type, position)
SELECT gen_random_uuid(), s.organization_id, s.worksite_id, u.user_id, s.notification_type, u.position
FROM recipient_settings s
JOIN recipient_setting_users u ON u.setting_id = s.id;

DROP TABLE recipient_setting_users;
DROP TABLE recipient_settings;
