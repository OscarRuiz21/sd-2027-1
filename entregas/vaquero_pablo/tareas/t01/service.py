"""Lógica compartida: no conoce HTTP, JSON, gRPC ni Protobuf."""
from dataclasses import dataclass


class InvalidId(ValueError):
    pass


class ProductNotFound(LookupError):
    pass


@dataclass(frozen=True)
class Product:
    id: int
    name: str
    price_cents: int


class CatalogService:
    def __init__(self):
        self._products = {
            1: Product(1, "Cuaderno", 4500),
            2: Product(2, "Lápiz", 1000),
        }

    def get_product(self, product_id):
        if type(product_id) is not int or not 1 <= product_id <= 2**31 - 1:
            raise InvalidId("El ID debe ser un entero entre 1 y 2147483647")
        product = self._products.get(product_id)
        if product is None:
            raise ProductNotFound(f"No hay datos para el ID {product_id}")
        return product
