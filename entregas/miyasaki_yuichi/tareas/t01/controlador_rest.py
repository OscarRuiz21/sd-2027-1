from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse

import nucleo

app = FastAPI(
    title="Catalogo (interfaz REST)",
    description="Misma logica que la interfaz gRPC; solo cambia el transporte.",
    version="1.0.0",
)


@app.get("/salud")
def salud():
    return {"estado": "ok", "interfaz": "rest"}


@app.get("/articulos")
def listar():
    return {"ids": nucleo.listar_ids()}


@app.get("/articulos/{id_articulo}")
def obtener(id_articulo: str):
    try:
        return nucleo.consultar_articulo(id_articulo)
    except nucleo.ArticuloNoEncontrado as e:
        raise HTTPException(status_code=404, detail=str(e))


@app.exception_handler(HTTPException)
def formato_error(request, exc: HTTPException):
    return JSONResponse(status_code=exc.status_code, content={"error": exc.detail})
