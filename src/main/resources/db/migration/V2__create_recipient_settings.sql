-- US45: destinatarios de notificación configurados por el administrador, por obra y tipo de aviso. Sin fila para una
-- obra y un tipo se aplica la regla por defecto (la de US43). Con fila, sus usuarios reemplazan a los destinatarios por
-- rol (la lista puede quedar vacía: nadie por rol); quien abrió una discrepancia sigue recibiendo los avisos de su caso.
-- Obra y usuarios vienen de las copias locales (location_projection, recipient_projection): sin llaves foráneas a
-- otros servicios.
CREATE TABLE recipient_settings (
    id                UUID PRIMARY KEY,
    organization_id   UUID        NOT NULL,
    worksite_id       UUID        NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    updated_by        UUID        NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_recipient_settings UNIQUE (organization_id, worksite_id, notification_type)
);

CREATE TABLE recipient_setting_users (
    setting_id UUID NOT NULL REFERENCES recipient_settings (id) ON DELETE CASCADE,
    user_id    UUID NOT NULL,
    position   INT  NOT NULL,
    PRIMARY KEY (setting_id, user_id)
);
