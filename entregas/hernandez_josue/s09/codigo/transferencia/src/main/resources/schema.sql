CREATE TABLE IF NOT EXISTS outbox (
    evento_id uuid PRIMARY KEY,
    clave varchar(40) NOT NULL,
    cuerpo text NOT NULL,
    creada timestamptz NOT NULL DEFAULT now(),
    publicada timestamptz
);

CREATE INDEX IF NOT EXISTS outbox_por_enviar
ON outbox (creada, evento_id) WHERE publicada IS NULL;
