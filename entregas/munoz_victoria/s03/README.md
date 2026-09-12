Patrón Idempotency-Key: ¿Por qué falla el Map y por qué el UNIQUE no?
1. El problema de la implementación ingenua (Map en memoria)
La implementación basada en un 'Map' en memoria falla debido a una condición de carrera conocida como TOCTOU (Time-of-Check to Time-of-Use). 
Entre el momento en que el hilo verifica si la llave existe ('check') y el momento en que procede a registrarla y cobrar ('use'), existe una ventana de tiempo infinitesimal. Si dos peticiones idénticas llegan de forma concurrente (por ejemplo, por un reintento automático de red tras un timeout), ambas pueden ver que la llave "no existe", pasar la validación al mismo tiempo y terminar ejecutando el cobro por duplicado. Además, si el servicio se escala horizontalmente en múltiples réplicas, cada contenedor tendrá su propio mapa aislado que no se comunica con los demás.

2. La solución correcta con base de datos (Restricción UNIQUE)
La unicidad se delega a un motor de base de datos relacional porque este sí garantiza atomicidad a nivel de almacenamiento. 
En lugar de revisar primero y actuar después, la estrategia correcta intenta insertar la llave de forma anticipada respaldada por una restricción 'UNIQUE'. El motor de base de datos se encarga de serializar las operaciones concurrentes: si dos peticiones intentan insertar la misma llave al mismo tiempo, la base de datos dejará pasar a una y rechazará a la otra lanzando una excepción por duplicidad ('DataIntegrityViolationException'). De este modo, solo una petición ejecuta el cobro real, mientras que la otra detecta el fallo por duplicado y se limita a devolver la respuesta guardada previamente.
-------------------------------------------------------------------
Pseudocódigo
1. Versión ingenua (Map en memoria - Falla):

FUNCTION procesar_cobro(idempotency_key, datos_pago):
    // Verificamos si la llave ya existe en el Map compartido
    IF memory_map.containsKey(idempotency_key) THEN
        RETURN memory_map.get(idempotency_key) // Devuelve respuesta previa
    END IF
    
    // --- AQUÍ OCURRE LA CARRERA (TOCTOU) ---
    // Dos hilos pueden pasar el if anterior al mismo tiempo si llegan juntos.
    
    // Ejecutamos el cobro real en la pasarela externa
    resultado = pasarela_externa.cobrar(datos_pago)
    
    // Guardamos en memoria
    memory_map.put(idempotency_key, resultado)
    
    RETURN resultado
END FUNCTION

2. Versión correcta (Restricción UNIQUE en Base de Datos):
FUNCTION procesar_cobro(idempotency_key, datos_pago):
    INICIAR TRANSACCIÓN DB
    
    TRY:
        // Intentamos insertar la llave de inmediato. La BD garantiza atomicidad.
        INSERT INTO idempotency_records (key, status) VALUES (idempotency_key, 'PROCESSING')
        
        // Si el INSERT pasa, somos el único hilo autorizado para cobrar
        resultado = pasarela_externa.cobrar(datos_pago)
        
        // Actualizamos el registro con el resultado final (código y cuerpo)
        UPDATE idempotency_records SET status = 'COMPLETED', response = resultado WHERE key = idempotency_key
        
        COMMIT TRANSACCIÓN
        RETURN resultado
        
    CATCH DuplicateKeyException:
        // Si la llave ya existía, el UNIQUE constraint frena el INSERT al instante
        ROLLBACK TRANSACCIÓN
        
        // Consultamos la respuesta guardada del proceso original y la devolvemos
        registro_previo = SELECT * FROM idempotency_records WHERE key = idempotency_key
        RETURN registro_previo.response
    END TRY
END FUNCTION