datos={
    "1": {"nombre": "Pedro Lopez", "area": "Computacion"},
    "2": {"nombre": "Maria Perez", "area": "Biologia"},
}

def buscar_por_id(id):
    item = datos.get(id)
    if item is None:
        return {"encontrado": False, "mensaje": "No hay datos para el ID ingresado"}
    return {"encontrado": True, **item}