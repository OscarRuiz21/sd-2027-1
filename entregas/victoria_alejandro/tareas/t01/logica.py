# Base de datos simulada
db = {
    1: {"nombre": "Leche Entera", "descripcion": "1 Litro", "precio": 25.50, "existencias": 50},
    2: {"nombre": "Pan de Caja", "descripcion": "Blanco, 680g", "precio": 42.00, "existencias": 30},
    3: {"nombre": "Manzanas", "descripcion": "Bolsa de 1kg, Gala", "precio": 55.00, "existencias": 15},
    4: {"nombre": "Cereal", "descripcion": "Hojuelas de maíz, 500g", "precio": 65.50, "existencias": 20},
    5: {"nombre": "Huevos", "descripcion": "Cartera con 12 piezas", "precio": 38.00, "existencias": 40}
}

def obtener_producto(producto_id):
    """Lógica central: recibe un ID y devuelve el diccionario del producto o None."""
    return db.get(producto_id)