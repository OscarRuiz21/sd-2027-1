"""Lógica de negocio compartida por REST y gRPC."""

PRODUCTOS = {
    1: {
        "id": 1,
        "nombre": "Laptop",
        "categoria": "Computacion",
        "disponible": True,
    },
    2: {
        "id": 2,
        "nombre": "Mouse",
        "categoria": "Accesorios",
        "disponible": True,
    },
    3: {
        "id": 3,
        "nombre": "Teclado",
        "categoria": "Accesorios",
        "disponible": False,
    },
}


def buscar_producto(producto_id: int):
    """Recibe un ID y devuelve el producto o None si no existe."""
    return PRODUCTOS.get(producto_id)
