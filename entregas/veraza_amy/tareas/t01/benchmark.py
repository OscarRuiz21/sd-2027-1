"""Medición reproducible de latencia, payload y tráfico de aplicación sobre TCP."""
import http.client
import json
import math
import os
from pathlib import Path
import select
import socket
import statistics
import threading
import time
from datetime import datetime, timezone

import grpc
import catalog_pb2 as pb
import catalog_pb2_grpc as rpc

N = 100
REST = (os.getenv("REST_HOST", "localhost"), int(os.getenv("REST_PORT", "8080")))
GRPC = (os.getenv("GRPC_HOST", "localhost"), 50051)


class CounterProxy:
    """Reenvía una conexión sin modificarla; cuenta bytes en ambas direcciones."""
    def __init__(self, target):
        self.target = target
        self.counts = [0, 0]
        self.error = None
        self.listener = socket.socket()
        self.listener.bind(("127.0.0.1", 0))
        self.listener.listen(1)
        self.address = self.listener.getsockname()
        self.thread = threading.Thread(target=self.run, daemon=True)
        self.thread.start()

    def run(self):
        try:
            with self.listener:
                client, _ = self.listener.accept()
                with client, socket.create_connection(self.target, timeout=5) as upstream:
                    peers = [client, upstream]
                    for peer in peers:
                        peer.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                    while True:
                        ready, _, _ = select.select(peers, [], [], 10)
                        if not ready:
                            raise TimeoutError("Proxy sin tráfico durante 10 segundos")
                        for source in ready:
                            data = source.recv(65536)
                            if not data:
                                return
                            direction = peers.index(source)
                            self.counts[direction] += len(data)
                            peers[1 - direction].sendall(data)
        except Exception as error:
            self.error = str(error)

    def snapshot(self):
        # Permite recibir los frames de control pendientes tras la respuesta.
        time.sleep(0.2)
        if self.error:
            raise RuntimeError(self.error)
        return self.counts.copy()


class Client:
    def __init__(self, kind, address):
        self.kind = kind
        if kind == "REST":
            self.connection = http.client.HTTPConnection(*address, timeout=5)
        else:
            self.connection = grpc.insecure_channel(f"{address[0]}:{address[1]}")
            self.stub = rpc.CatalogStub(self.connection)

    def call(self):
        if self.kind == "REST":
            self.connection.request("GET", "/products/1")
            response = self.connection.getresponse()
            payload = response.read()
            assert response.status == 200 and json.loads(payload)["name"] == "Teclado"
            return payload
        response = self.stub.GetProduct(pb.GetProductRequest(id=1), timeout=5)
        assert response.name == "Teclado"
        return response.SerializeToString()

    def close(self):
        self.connection.close()


def measure(kind, address):
    client = Client(kind, address)
    try:
        for _ in range(10):
            payload = client.call()
        samples = []
        for _ in range(N):
            start = time.perf_counter_ns()
            client.call()
            samples.append((time.perf_counter_ns() - start) / 1e6)
    finally:
        client.close()
    proxy = CounterProxy(address)
    client = Client(kind, proxy.address)
    try:
        client.call()
        first = proxy.snapshot()
        for _ in range(N):
            client.call()
        total = proxy.snapshot()
    finally:
        client.close()
        proxy.thread.join(timeout=5)
    return {
        "response_payload_bytes": len(payload),
        "latency_ms": {"mean": round(statistics.mean(samples), 3), "median": round(statistics.median(samples), 3), "p95": round(sorted(samples)[math.ceil(N * .95) - 1], 3)},
        "first_call_tcp_payload_bytes": {"client_to_server": first[0], "server_to_client": first[1], "total": sum(first)},
        "warm_call_tcp_payload_bytes_mean": {"client_to_server": (total[0] - first[0]) / N, "server_to_client": (total[1] - first[1]) / N, "total": (sum(total) - sum(first)) / N},
    }


def lines(path):
    content = Path(path).read_text().splitlines()
    return {"physical": len(content), "nonblank": sum(bool(line.strip()) for line in content)}


if __name__ == "__main__":
    print(json.dumps({
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "samples_per_protocol": N,
        "warmup_calls": 10,
        "environment": "Cliente Python y servidores en red Docker Compose; sin TLS ni compresión",
        "wire_scope": "Bytes de aplicación TCP observados en proxy: incluye HTTP/1.1 o HTTP/2 y gRPC; excluye cabeceras TCP/IP/Ethernet, ACK y retransmisiones. Primera llamada incluye establecimiento HTTP/2; cierre excluido. Tráfico posterior: promedio de 100 llamadas en la misma conexión, con 200 ms para frames pendientes.",
        "latency_scope": "Llamadas secuenciales con conexión reutilizada, sin proxy; incluye serialización y comprobación de respuesta del cliente.",
        "REST": measure("REST", REST),
        "gRPC": measure("gRPC", GRPC),
        "contract_lines": {"openapi.yaml": lines("openapi.yaml"), "proto/catalog.proto": lines("proto/catalog.proto")},
    }, indent=2, ensure_ascii=False))
