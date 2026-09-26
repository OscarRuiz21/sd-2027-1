"""Caso de uso compartido: consultar un producto de un catálogo fijo."""

PRODUCTS = {
    1: {"id": 1, "name": "Teclado", "price_cents": 59900, "stock": 12},
    2: {"id": 2, "name": "Mouse", "price_cents": 24900, "stock": 30},
    3: {"id": 3, "name": "Monitor", "price_cents": 329900, "stock": 5},
}


def get_product(product_id):
    if not 1 <= product_id <= 2147483647:
        raise ValueError("El ID debe estar entre 1 y 2147483647")
    if product_id not in PRODUCTS:
        raise KeyError("Producto no encontrado")
    return PRODUCTS[product_id].copy()
