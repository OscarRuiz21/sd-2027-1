# T01 — El mismo servicio, por REST (en Django) y por gRPC 

## Diseño

La lógica vive en  logica.py con una función buscar_por_id. 
Tanto server_rest.py (Django) como server_grpc.py importan esa misma función.
Ninguno de los dos tiene lógica propia.

## ¿Qué cambia entre un controlador y otro?

- REST (server_rest.py): una vista de Django que recibe el id desde
  la URL (/items/<id>/), llama a buscar_por_id, y devuelve JSON con
  el código HTTP.
- gRPC (server_grpc.py): un servicio gRPC generado a partir de
  servicio.proto, que recibe el id en un mensaje SolicitudId, llama
  a la misma buscar_por_id, y devuelve un RespuestaItem.

## ¿Cómo se levanta?

docker compose up --build

Levanta el servidor REST (:3000), el servidor gRPC (:50051), y un
cliente que prueba ambos automáticamente.

## Evidencia

servidor-grpc-1  | gRPC escuchando en :50051
servidor-rest-1  | [20/Sep/2026 02:14:59] "GET /items/1/ HTTP/1.1" 200 66
servidor-grpc-1  | Bytes gRPC: 13
servidor-rest-1  | [20/Sep/2026 02:14:59] "GET /items/2/ HTTP/1.1" 200 74
servidor-grpc-1  | Bytes gRPC: 21
servidor-rest-1  | [20/Sep/2026 02:15:00] "GET /items/3/ HTTP/1.1" 200 71
servidor-grpc-1  | Bytes gRPC: 18
cliente-1        | [REST] GET /items/1/ -> 200 {'encontrado': True, 'id': '1', 'nombre': 'Makima', 'mensaje': ''}
cliente-1        | [gRPC] Buscar(1) -> encontrado: true id: "1" nombre: "Makima"
servidor-rest-1  | Not Found: /items/99/
servidor-rest-1  | [20/Sep/2026 02:15:00] "GET /items/99/ HTTP/1.1" 404 84
cliente-1        | [REST] GET /items/2/ -> 200 {'encontrado': True, 'id': '2', 'nombre': 'Mai Sakurajima', 'mensaje': ''}
cliente-1        | [gRPC] Buscar(2) -> encontrado: true id: "2" nombre: "Mai Sakurajima"
cliente-1        | [REST] GET /items/3/ -> 200 {'encontrado': True, 'id': '3', 'nombre': 'Rei Ayanami', 'mensaje': ''}
cliente-1        | [gRPC] Buscar(3) -> encontrado: true id: "3" nombre: "Rei Ayanami"
cliente-1        | [REST] GET /items/99/ -> 404 {'encontrado': False, 'id': '', 'nombre': '', 'mensaje': 'No hay datos para ese ID'}
cliente-1        | [gRPC] Buscar(99) -> mensaje: "No hay datos para ese ID"

## Medición de bytes

- REST/JSON: 66 bytes para el id 1, medido con:
  curl.exe -s http://localhost:3000/items/1/ | Measure-Object -Character
  que cuenta los caracteres de la respuesta completa.

- gRPC/Protobuf: 13 bytes para el id 1, medido agregando
  len(mensaje.SerializeToString()) en server_grpc.py, justo antes de
  devolver la respuesta e imprimiendo el resultado en la consola del
  servidor con print().

La diferencia es de aproximadamente 5 veces menos en gRPC (13 bytes
contra 66). Esto pasa porque JSON repite el nombre de cada campo como
texto ("encontrado", "id", "nombre", "mensaje") en cada respuesta,
mientras que Protobuf solo manda los valores identificados por un
número de campo, sin repetir ningún nombre de texto. Además, en este
caso Protobuf ni siquiera manda los campos que están vacíos o en su
valor por defecto (como el campo "mensaje" cuando está vacío), lo
que reduce aún más el tamaño. 