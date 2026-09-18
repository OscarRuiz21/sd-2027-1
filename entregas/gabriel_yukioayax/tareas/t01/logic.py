db = {
    "1": "Informacion del usuario 1",
    "2": "Informacion del usuario 2"
}

def consultar_datos(id_busqueda):
    if id_busqueda in db:
        return True, db[id_busqueda]
    return False, "No hay datos"
