BASE_DATOS = {
    "1": {"nombre": "Libro de Redes", "detalle": "Tanenbaum - Cap 1"},
    "2": {"nombre": "Laptop", "detalle": "Estacion de trabajo de computo"},
    "3": {"nombre": "Token de Acceso", "detalle": "Credencial efimera"}
}

def consultar_item(item_id: str):
    item = BASE_DATOS.get(str(item_id))
    if item:
        return {
            "id": str(item_id),
            "nombre": item["nombre"],
            "detalle": item["detalle"],
            "encontrado": True
        }
    return {
        "id": str(item_id),
        "nombre": "",
        "detalle": "Item no encontrado",
        "encontrado": False
    }
