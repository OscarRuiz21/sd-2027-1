from fastapi import FastAPI, Header, HTTPException, status
from pydantic import BaseModel
import asyncio
import random

app = FastAPI()

class PagoRequest(BaseModel):
    monto: float

db_records = {}

@app.post("/cobrar", status_code=status.HTTP_201_CREATED)
async def cobrar(pago: PagoRequest, idempotency_key: str = Header(None)):
    if not idempotency_key:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Falta la cabecera Idempotency-Key"
        )

    if idempotency_key in db_records:
        record = db_records[idempotency_key]
        
        if record["status"] == "PROCESSING":
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="La petición con esta llave ya está en vuelo"
            )
        
        return record["response_body"]

    db_records[idempotency_key] = {"status": "PROCESSING"}

    try:
        await asyncio.sleep(1)

        response_body = {
            "mensaje": "Cobro exitoso",
            "monto": pago.monto,
            "transaccion_id": random.randint(10000, 99999)
        }
        
        db_records[idempotency_key] = {
            "status": "COMPLETED",
            "response_body": response_body
        }

        return response_body

    except Exception as e:
        del db_records[idempotency_key]
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Error interno procesando el pago"
        )