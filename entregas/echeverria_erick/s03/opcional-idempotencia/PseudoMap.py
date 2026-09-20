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