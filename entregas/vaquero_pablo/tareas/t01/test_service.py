"""Pruebas con sockets reales y un servicio espía compartido."""
import unittest
from client import call_grpc, call_rest
from server import Server
from service import CatalogService, InvalidId, ProductNotFound


class ServiceTests(unittest.TestCase):
    def test_found_and_missing(self):
        service = CatalogService()
        self.assertEqual(service.get_product(1).name, "Cuaderno")
        with self.assertRaises(ProductNotFound):
            service.get_product(999)

    def test_invalid_ids(self):
        for value in (0, -1, 2**31, None, "1", True):
            with self.subTest(value=value), self.assertRaises(InvalidId):
                CatalogService().get_product(value)


class RecordingCatalog(CatalogService):
    def __init__(self):
        super().__init__()
        self.calls = []

    def get_product(self, product_id):
        self.calls.append(product_id)
        return super().get_product(product_id)


class TransportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.service = RecordingCatalog()
        cls.server = Server("127.0.0.1", 0, 0, cls.service)
        cls.server.start()
        cls.rest = f"http://127.0.0.1:{cls.server.rest_port}"
        cls.grpc = f"127.0.0.1:{cls.server.grpc_port}"

    @classmethod
    def tearDownClass(cls):
        cls.server.stop()

    def test_same_instance_receives_both_calls(self):
        self.service.calls.clear()
        call_rest(self.rest, 1)
        call_grpc(self.grpc, 1)
        self.assertEqual(self.service.calls, [1, 1])

    def test_existing_products(self):
        for product_id, name, price in ((1, "Cuaderno", 4500), (2, "Lápiz", 1000)):
            with self.subTest(id=product_id):
                rest = call_rest(self.rest, product_id)
                rpc = call_grpc(self.grpc, product_id)
                self.assertEqual(rest[:2], rpc[:2])
                self.assertEqual(rest[:2], ("OK", {"id": product_id, "name": name, "price_cents": price}))
                self.assertEqual(rest[3], "200")

    def test_missing(self):
        rest = call_rest(self.rest, 999)
        rpc = call_grpc(self.grpc, 999)
        self.assertEqual(rest[:2], rpc[:2])
        self.assertEqual(rest[0], "NOT_FOUND")
        self.assertEqual(rest[3], "404")

    def test_invalid(self):
        for product_id in (0, -1):
            with self.subTest(id=product_id):
                rest = call_rest(self.rest, product_id)
                rpc = call_grpc(self.grpc, product_id)
                self.assertEqual(rest[:2], rpc[:2])
                self.assertEqual(rest[0], "INVALID_ARGUMENT")
                self.assertEqual(rest[3], "400")

    def test_text_id_rest(self):
        self.assertEqual(call_rest(self.rest, "abc")[0], "INVALID_ARGUMENT")


if __name__ == "__main__":
    unittest.main(verbosity=2)
