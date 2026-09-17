DB = {
    "1": {"id": "1", "nombre": "A", "tipo": "directivo", "activo": True},
    "2": {"id": "2", "nombre": "B", "tipo": "profesor", "activo": True},
    "3": {"id": "3", "nombre": "C", "tipo": "alumno", "activo": False},
}


def get_item_by_id(item_id):
    item = DB.get(str(item_id))
    if item is None:
        return {"found": False}
    return {"found": True, "item": item}