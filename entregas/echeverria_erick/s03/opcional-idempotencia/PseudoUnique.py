TABLA idempotency_keys (
    key VARCHAR PRIMARY KEY,
    status VARCHAR,
    response_body JSON,
    created_at TIMESTAMP
)

function procesarCobroCorrecto(request):
    key = request.headers["Idempotency-Key"]

    intento_insert = BD.ejecutar(
        "INSERT INTO idempotency_keys(key, status, created_at)
        VALUES (key, 'PROCESSING', NOW())
        ON CONFLICT DO NOTHING"
    )

    if intento_insert.filas_afectadas == 0:
        registro = BD.consultar("SELECT * FROM idempotency_keys WHERE key = key")

        if registro.status == 'PROCESSING':
            return "Operación en proceso"

        if registro.status == 'COMPLETED':
            return registro.response_body

    try:
        resultado = ejecutarCobroEnBanco(request.monto)

        BD.ejecutar(
            "UPDATE idempotency_keys
            SET status = 'COMPLETED', response_body = resultado
            WHERE key = key"
        )
        return resultado

    catch error:
        BD.ejecutar(
            "UPDATE idempotency_keys SET status = 'FAILED'
            WHERE key = key"
        )
        return "Error al procesar el cobro"