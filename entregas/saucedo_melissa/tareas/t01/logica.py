# logica.py
# Aquí vive la única fuente de verdad del servicio.
# Tanto el servidor REST como el servidor gRPC llaman a estas funciones.
# Ninguno de los dos vuelve a escribir esta lógica por su cuenta.

BASE_DATOS = {
    "1": {"nombre": "Melissa Saucedo", "carrera": "Ingenieria en Computacion"},
    "2": {"nombre": "Ana Torres", "carrera": "Ingenieria Industrial"},
    "3": {"nombre": "Luis Ramirez", "carrera": "Ingenieria Mecanica"},
}


def get_item(item_id):
    """
    Busca un item por su id en la base de datos en memoria.
    Devuelve un diccionario con encontrado=True y los datos,
    o encontrado=False si el id no existe.
    Esta es la unica funcion que sabe como buscar datos:
    ni el controlador REST ni el controlador gRPC repiten esta logica,
    solo la llaman y traducen el resultado a su propio formato.
    """
    item = BASE_DATOS.get(item_id)
    if item is None:
        return {"encontrado": False}
    return {"encontrado": True, "nombre": item["nombre"], "carrera": item["carrera"]}