CREATE TABLE IF NOT EXISTS evento_procesado (
    evento_id uuid PRIMARY KEY,
    recibido timestamptz NOT NULL DEFAULT now()
);
