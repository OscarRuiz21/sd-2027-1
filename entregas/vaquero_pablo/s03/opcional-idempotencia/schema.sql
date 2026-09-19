CREATE TABLE IF NOT EXISTS idempotency_keys (
    key TEXT NOT NULL UNIQUE,
    request_hash TEXT NOT NULL,
    response_status INTEGER,
    response_body BLOB,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        (response_status IS NULL AND response_body IS NULL) OR
        (response_status = 201 AND response_body IS NOT NULL)
    )
);

CREATE TABLE IF NOT EXISTS charges (
    id TEXT PRIMARY KEY NOT NULL,
    idempotency_key TEXT NOT NULL UNIQUE REFERENCES idempotency_keys(key),
    amount INTEGER NOT NULL CHECK (amount > 0),
    currency TEXT NOT NULL CHECK (currency IN ('MXN', 'USD', 'EUR')),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
