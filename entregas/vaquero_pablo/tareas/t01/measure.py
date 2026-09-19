"""Cuenta bytes reales del flujo TCP usando un intermediario transparente."""
import errno
import json
import os
import selectors
import socket
import threading
from urllib.parse import urlsplit
from client import call_grpc, call_rest


class CountingProxy:
    def __init__(self, host, port):
        self.target = (host, port)
        self.sent = 0
        self.received = 0
        self.error = None
        self.listener = socket.socket()
        self.listener.bind(("127.0.0.1", 0))
        self.listener.listen(1)
        self.listener.settimeout(10)
        self.port = self.listener.getsockname()[1]
        self.thread = threading.Thread(target=self.forward, daemon=True)

    def forward(self):
        try:
            with self.listener:
                client, _ = self.listener.accept()
                with client, socket.create_connection(self.target, timeout=5) as upstream:
                    client.settimeout(5)
                    with selectors.DefaultSelector() as selector:
                        selector.register(client, selectors.EVENT_READ, (upstream, "sent"))
                        selector.register(upstream, selectors.EVENT_READ, (client, "received"))
                        while selector.get_map():
                            events = selector.select(timeout=10)
                            if not events:
                                raise TimeoutError("La conexión no terminó; medición incompleta")
                            for key, _ in events:
                                source = key.fileobj
                                destination, counter = key.data
                                data = source.recv(65536)
                                if not data:
                                    selector.unregister(source)
                                    try:
                                        destination.shutdown(socket.SHUT_WR)
                                    except OSError as error:
                                        if error.errno != errno.ENOTCONN:
                                            raise
                                    continue
                                destination.sendall(data)
                                setattr(self, counter, getattr(self, counter) + len(data))
        except Exception as error:
            self.error = error

    def measure(self, call):
        self.thread.start()
        result = call(self.port)
        self.thread.join(timeout=15)
        if self.thread.is_alive():
            raise TimeoutError("El intermediario no terminó")
        if self.error:
            raise self.error
        if result[0] != "OK":
            raise RuntimeError("La consulta medida debe ser exitosa")
        return {"request_stream_bytes": self.sent,
                "response_stream_bytes": self.received,
                "total_stream_bytes": self.sent + self.received,
                "response_body_bytes": result[2]}, result[1]


def main():
    rest = urlsplit(os.getenv("REST_URL", "http://localhost:8000"))
    grpc_host, grpc_port = os.getenv("GRPC_TARGET", "localhost:50051").rsplit(":", 1)
    measurements = {}
    measurements["REST"], rest_data = CountingProxy(rest.hostname, rest.port or 80).measure(
        lambda port: call_rest(f"http://127.0.0.1:{port}", 1))
    measurements["gRPC"], grpc_data = CountingProxy(grpc_host, int(grpc_port)).measure(
        lambda port: call_grpc(f"127.0.0.1:{port}", 1))
    if rest_data != grpc_data:
        raise RuntimeError("Las consultas no devolvieron el mismo producto")
    print(json.dumps({"id": 1, "connection": "fresh, plaintext, one RPC/request",
                      "scope": "TCP payload in both directions; excludes TCP/IP/Ethernet headers",
                      "measurements": measurements}, indent=2))


if __name__ == "__main__":
    main()
