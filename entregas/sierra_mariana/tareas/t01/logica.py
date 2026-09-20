db = {
    1: "Informacion confidencial del usuario 1",
    2: "Datos generales del sistema"
}

def obtener_datos(id_buscado):
    if id_buscado in db:
        return True, db[id_buscado]
    return False, "No hay datos"