# Evidencia de T01

Verificación realizada el **19 de septiembre de 2026** con Docker Desktop en macOS
arm64. Docker Engine 28.3.3, Compose 2.39.2, imagen `python:3.12-slim`,
grpcio/grpcio-tools 1.76.0 y Protobuf 6.33.0. Los contenedores ejecutan Linux arm64.
Las marcas de tiempo de los logs están en UTC.

## Cliente y servidor en contenedores diferentes

Comando ejecutado desde esta carpeta:

```bash
docker compose up --build --abort-on-container-exit --exit-code-from client
```

Resultado: construcción y ejecución correctas, cliente con **código de salida 0**.
Compose creó `t01-vaquero-pablo_catalog`; el cliente contactó al servidor por los
nombres y puertos internos de Docker. El log incluye solicitudes desde la IP
del cliente, diferente de las comprobaciones de salud desde `127.0.0.1`.

| ID | REST | gRPC | Comparación |
| --- | --- | --- | --- |
| 1 | 200; Cuaderno, 4500 centavos | OK; mismos campos | Coinciden |
| 2 | 200; Lápiz, 1000 centavos | OK; mismos campos | Coinciden, incluido UTF-8 |
| 999 | 404 | NOT_FOUND | Mismo mensaje de ausencia |
| 0 | 400 | INVALID_ARGUMENT | Mismo mensaje de validación |

[Salida de la demostración](evidencia/docker-demo.txt). Se conserva el tramo de
ejecución desde que Compose conecta los logs; se omite la salida de construcción
y se eliminan los códigos de control de la terminal.

## Pruebas automatizadas

```bash
docker compose run --rm --no-deps server python -m unittest -v
```

Resultado: **7 pruebas, OK**, código de salida 0.
Estas pruebas verifican respuestas esperadas, no solo igualdad entre protocolos.
La prueba `test_same_instance_receives_both_calls` confirma que el mismo objeto
registra las dos consultas. [Salida completa](evidencia/pruebas.txt).

## Bytes de la misma consulta

```bash
docker compose up -d --wait server
docker compose run --rm --no-deps --entrypoint python client measure.py
```

Resultado: código de salida 0. [Salida JSON](evidencia/medicion.json).

| Alcance | REST | gRPC |
| --- | ---: | ---: |
| Cuerpo de respuesta | 45 bytes | 15 bytes |
| Flujo TCP en ambos sentidos | 334 bytes | 669 bytes |

Es una conexión nueva por protocolo, sin TLS, consultando ID 1. El flujo incluye
encabezados y mensajes de control de aplicación, pero no cabeceras ni tráfico de
control de TCP/IP/Ethernet. El [README](README.md#medición-de-bytes) explica el
método, sus límites y por qué una respuesta binaria menor no garantiza una conexión
completa menor en una llamada aislada.
