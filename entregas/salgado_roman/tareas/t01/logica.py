# db simulada en memoria
db_memoria = {
    "100": {"datos": "Emiliano Salgado - Ing. Computación UNAM"},
    "200": {"datos": "Reni - Registro 200"}
}

def consultar_datos(id_consulta: str) -> dict:
    if id_consulta in db_memoria:
        return {"id": id_consulta, "datos": db_memoria[id_consulta]["datos"], "error": ""}
    return {"id": id_consulta, "datos": "", "error": "No hay datos"}