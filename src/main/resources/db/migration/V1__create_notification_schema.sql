-- Notificaciones (notification_db), portado de V22 del Core (stash del Sprint 3). Las notificaciones se crean solo
-- a partir de eventos de los demás contextos; recipient_user_id es un usuario de iam (copia en
-- recipient_projection): sin llave foránea.
CREATE TABLE notifications (
    id                UUID PRIMARY KEY,
    organization_id   UUID         NOT NULL,
    recipient_user_id UUID         NOT NULL,
    type              VARCHAR(40)  NOT NULL,
    title             VARCHAR(150) NOT NULL,
    body              VARCHAR(500) NOT NULL,
    link              VARCHAR(200) NOT NULL,
    source_event_id   UUID,
    created_at        TIMESTAMPTZ  NOT NULL,
    read_at           TIMESTAMPTZ
);

CREATE INDEX idx_notifications_recipient
    ON notifications (organization_id, recipient_user_id, created_at DESC, id DESC);
CREATE INDEX idx_notifications_unread
    ON notifications (organization_id, recipient_user_id) WHERE read_at IS NULL;
-- Un mismo evento no notifica dos veces a la misma persona.
CREATE UNIQUE INDEX uk_notifications_event_recipient
    ON notifications (source_event_id, recipient_user_id) WHERE source_event_id IS NOT NULL;

-- Copia de los usuarios de iam (UserCreated, UserUpdated): a quién avisar, su correo y si sigue activo.
CREATE TABLE recipient_projection (
    user_id         UUID PRIMARY KEY,
    organization_id UUID         NOT NULL,
    full_name       VARCHAR(150),
    email           VARCHAR(254),
    role            VARCHAR(30)  NOT NULL,
    active          BOOLEAN      NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_recipient_projection_role ON recipient_projection (organization_id, role) WHERE active;

-- Copia de las asignaciones de organization (StaffAssigned, StaffUnassigned): encargados de cada obra y almacén.
CREATE TABLE assignment_projection (
    assignment_id   UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    location_id     UUID        NOT NULL,
    location_type   VARCHAR(20) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_assignment_projection_location ON assignment_projection (location_id);

-- Copia de obras y almacenes de organization (LocationRegistered, LocationUpdated): nombres para los mensajes.
CREATE TABLE location_projection (
    location_id     UUID PRIMARY KEY,
    organization_id UUID         NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    name            VARCHAR(150) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

-- Datos de cada despacho que traen los eventos de ordering: la incidencia en ruta solo trae el dispatchId.
CREATE TABLE dispatch_projection (
    dispatch_id     UUID PRIMARY KEY,
    organization_id UUID        NOT NULL,
    manifest_code   VARCHAR(40),
    worksite_id     UUID,
    warehouse_id    UUID,
    updated_at      TIMESTAMPTZ NOT NULL
);

-- Consumidor idempotente (kernel compartido): un evento se procesa una sola vez.
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
