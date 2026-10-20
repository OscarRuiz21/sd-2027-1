BASE_DE_DATOS = {
    1: {"nombre": "Uriel Guerrero", "email": "uriel@unam.mx"},
    2: {"nombre": "Ana Ramirez", "email": "ana@unam.mx"}
}

def buscar_usuario_por_id(usuario_id: int):
    if usuario_id in BASE_DE_DATOS:
        data = BASE_DE_DATOS[usuario_id]
        return {
            "id": usuario_id,
            "nombre": data["nombre"],
            "email": data["email"],
            "encontrado": True
        }
    return {
        "id": usuario_id,
        "nombre": "",
        "email": "",
        "encontrado": False
    }