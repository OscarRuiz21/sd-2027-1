# Tarea Opcional S03 - Idempotency Key y condición de carrera

## Explicación

La versión con Map tiene un problema porque primero revisa si la llave existe y después realiza el cobro y la guarda. Si dos peticiones llegan casi al mismo tiempo, las dos pueden hacer la revisión antes de que alguna guarde la llave. De esta forma, ambas pueden continuar con el cobro. Este problema se conoce como TOCTOU (Time-Of-Check to Time-Of-Use).

Para evitarlo, la llave se registra en la base de datos antes de realizar el cobro y la tabla utiliza UNIQUE o PRIMARY KEY. Así, si dos peticiones intentan registrar la misma llave, solo una puede hacerlo. La otra detecta que la llave ya existe y no vuelve a ejecutar el cobro. Junto con la llave se puede guardar el estado de la operación (PROCESSING, COMPLETED o FAILED), la respuesta original y la fecha de creación. También se puede establecer un tiempo de expiración para eliminar llaves antiguas.

Un caso más complicado ocurre si el servidor falla después de guardar la llave pero durante el proceso del cobro. La llave podría quedar en PROCESSING, e incluso podría ocurrir que el cobro se haya realizado pero no se haya alcanzado a guardar COMPLETED. Por eso, en un sistema real no sería lo mas seguro simplemente marcarla como FAILED y volver a cobrar. Primero habría que comprobar si el cobro realmente ocurrió, por ejemplo consultando el estado de la transacción en el proveedor de pagos.

En resumen, UNIQUE evita la carrera que existe con el Map, pero también es necesario manejar correctamente los fallos que pueden ocurrir durante el proceso.

## Pseudocódigo

### Versión con map (falla)

```bash
idempotency_map = Map()

function procesarCobroIngenuo(request):

    key = request.headers["Idempotency-Key"]

    if key in idempotency_map:
        return idempotency_map[key]

    // Aquí existe una condición:
    // otra petición puede pasar el mismo check antes de que esta petición guarde la llave.

    resultado = ejecutarCobroEnBanco(request.monto)
    idempotency_map[key] = resultado

    return resultado
```

### Versión con base de datos (correcta)

```bash
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
```
