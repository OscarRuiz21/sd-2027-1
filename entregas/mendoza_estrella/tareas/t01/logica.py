BASE = {
    "1": {"id": "1", "nombre": "Makima"},
    "2": {"id": "2", "nombre": "Mai Sakurajima"},
    "3": {"id": "3", "nombre": "Rei Ayanami"},
}


def buscar_por_id(item_id):
    item = BASE.get(item_id)
    if not item:
        return {"encontrado": False, "id": "", "nombre": "", "mensaje": "No hay datos para ese ID"}
    return {"encontrado": True, "id": item["id"], "nombre": item["nombre"], "mensaje": ""}