"""Única implementación del caso de uso; no depende de HTTP ni de gRPC."""
PRODUCTOS = {1: {"id": 1, "nombre": "Cuaderno", "precio_centavos": 4500},
             2: {"id": 2, "nombre": "Mochila", "precio_centavos": 39900}}

def consultar(producto_id):
    if producto_id not in PRODUCTOS:
        raise KeyError("Producto inexistente")
    return PRODUCTOS[producto_id].copy()
