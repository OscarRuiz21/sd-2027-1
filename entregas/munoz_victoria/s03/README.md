Opcional S03 · Implementa bien el patrón de idempotency key

Por qué el Map en memoria falla y el UNIQUE no
La implementación ingenua de una llave de idempotencia utilizando un mapa en memoria (`Map`) presenta fallas fundamentales en entornos concurrentes y distribuidos:
La condición de carrera TOCTOU (Time-Of-Check to Time-Of-Use): Entre el instante en que el sistema verifica si la llave ya existe en el mapa y el instante en que efectivamente la registra y procesa el pago, transcurre una ventana de tiempo infinitesimal. Si dos peticiones idénticas llegan de manera concurrente, ambas evaluarán el mapa como "no visto", sobrepasarán la validación de forma simultánea y provocarán un cobro doble.

Aislamiento entre réplicas: En una arquitectura moderna con múltiples instancias o réplicas de un microservicio, cada contenedor o servidor mantiene su propia memoria RAM y, por ende, su propio mapa independiente, volviendo imposible la sincronización del estado global de las peticiones.

Por el contrario, delegar la unicidad en una restricción `UNIQUE` a nivel de base de datos resuelve el problema de forma atómica:
Serialización nativa: La base de datos opera como un coordinador centralizado que serializa de manera estricta las escrituras concurrentes.
Control transaccional: Al intentar insertar la llave antes de procesar el cobro, el motor de la base de datos permite que solo una petición triunfe mientras rechaza de inmediato cualquier intento duplicado mediante una excepción de integridad de datos. De este modo, la aplicación intercepta dicho fallo y devuelve la respuesta original sin reejecutar la transacción.
----------------------------------------------------------------------
Pseudocódigo de las versiones
Global memoryMap = new Map<String, Response>()

Endpoint POST /cobrar(Header Idempotency-Key, Request payload):
    // 1. Check (Vulnerable a TOCTOU)
    if memoryMap.containsKey(Idempotency-Key):
        return memoryMap.get(Idempotency-Key) // Devuelve respuesta anterior

    // --- VENTANA DE CARRERA (TOCTOU) ---
    
    // 2. Procesar cobro externo
    response = PaymentGateway.charge(payload)

    // 3. Use / Guardar
    memoryMap.put(Idempotency-Key, response)
    
    return response