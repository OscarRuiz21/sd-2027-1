import http.client
import json
import os
import unittest

import grpc
import catalog_pb2 as pb
import catalog_pb2_grpc as rpc


class ServicesTest(unittest.TestCase):
    def setUp(self):
        self.http = http.client.HTTPConnection(os.getenv("REST_HOST", "localhost"), int(os.getenv("REST_PORT", "8080")), timeout=5)
        self.channel = grpc.insecure_channel(os.getenv("GRPC_HOST", "localhost") + ":50051")
        self.stub = rpc.CatalogStub(self.channel)

    def tearDown(self):
        self.http.close()
        self.channel.close()

    def request(self, path, method="GET"):
        self.http.request(method, path)
        response = self.http.getresponse()
        return response.status, json.loads(response.read())

    def test_same_products(self):
        expected = [(1, "Teclado", 59900, 12), (2, "Mouse", 24900, 30), (3, "Monitor", 329900, 5)]
        for product_id, name, price, stock in expected:
            with self.subTest(id=product_id):
                status, body = self.request(f"/products/{product_id}")
                result = self.stub.GetProduct(pb.GetProductRequest(id=product_id), timeout=5)
                self.assertEqual(status, 200)
                self.assertEqual(body, dict(id=product_id, name=name, price_cents=price, stock=stock))
                self.assertEqual(body, dict(id=result.id, name=result.name, price_cents=result.price_cents, stock=result.stock))

    def test_errors(self):
        for product_id, http_code, grpc_code in [(0, 400, grpc.StatusCode.INVALID_ARGUMENT), (-1, 400, grpc.StatusCode.INVALID_ARGUMENT), (999, 404, grpc.StatusCode.NOT_FOUND), (2147483647, 404, grpc.StatusCode.NOT_FOUND)]:
            with self.subTest(id=product_id):
                status, body = self.request(f"/products/{product_id}")
                self.assertEqual(status, http_code)
                with self.assertRaises(grpc.RpcError) as caught:
                    self.stub.GetProduct(pb.GetProductRequest(id=product_id), timeout=5)
                self.assertEqual(caught.exception.code(), grpc_code)
                self.assertEqual(body["error"], caught.exception.details())

    def test_invalid_rest_input(self):
        for value in ["abc", "1.5", "2147483648", "999999999999999"]:
            with self.subTest(value=value):
                self.assertEqual(self.request(f"/products/{value}")[0], 400)

    def test_unknown_route_and_method(self):
        self.assertEqual(self.request("/missing")[0], 404)
        self.assertEqual(self.request("/products/1", "POST")[0], 405)


if __name__ == "__main__":
    unittest.main()
