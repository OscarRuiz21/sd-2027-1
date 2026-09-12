@app.post("/cobrar")
def cobrar(request_payload, idempotency_key: str = Header(...)):
    try:
        # 1. Intento de inserción atómica y reserva de la llave (Respaldado por UNIQUE en la BD)
        database.execute(
            "INSERT INTO idempotency_records (key, status) VALUES (?, 'PROCESSING')", 
            idempotency_key
        )
        
        # 2. Procesar el cobro si el registro es exclusivo
        response = payment_gateway.charge(request_payload)
        
        # 3. Actualizar el registro con la respuesta obtenida para futuros reintentos
        database.execute(
            "UPDATE idempotency_records SET response = ?, status = 'COMPLETED' WHERE key = ?", 
            response, idempotency_key
        )
        
        return response, 201
        
    except DataIntegrityViolationException:
        # 4. Si la llave ya existía (colisión por concurrencia simultánea o reintento), 
        # se recupera la respuesta previa sin ejecutar el cobro de nuevo[cite: 1]
        existing_record = database.query(
            "SELECT response FROM idempotency_records WHERE key = ?", 
            idempotency_key
        )
        
        return existing_record.response, 200