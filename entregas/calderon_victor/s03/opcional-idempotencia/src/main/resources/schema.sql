-- Tabla de llaves de idempotencia
-- La restricción PRIMARY KEY en key_id garantiza unicidad de forma atómica
-- en el motor de base de datos: es la pieza central del patrón correcto.
CREATE TABLE IF NOT EXISTS idempotency_keys (
    key_id        VARCHAR(64)  PRIMARY KEY,
    status        VARCHAR(20)  NOT NULL,
    response_body TEXT,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
