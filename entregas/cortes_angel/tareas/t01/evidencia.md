angel@LAPTOP-5TAD6L7D MINGW64 /d/SD-2027-1/sd-2027-1/entregas/cortes_angel/tareas/t01/client (entregas_cortes_angel)
$ docker compose up --build
 ✔ Image t01-client       Built                                                                                                                                                                               1.6s
 ✔ Image t01-server       Built                                                                                                                                                                               1.6s
 ✔ Container t01-server-1 Running                                                                                                                                                                             0.0s
Attaching to client-1, server-1
server-1  | Servidor gRPC escuchando en el puerto 50051...
server-1  | Servidor REST escuchando en el puerto 3000...
server-1  | INFO:     172.18.0.3:59192 - "GET /api/consulta/101 HTTP/1.1" 200 OK
server-1  | INFO:     172.18.0.3:59202 - "GET /api/consulta/999 HTTP/1.1" 404 Not Found

client-1  | >>> BUSCANDO UN ID QUE SÍ EXISTE (101)
client-1  |
client-1  | --- Prueba REST (ID: 101) ---
client-1  | Status HTTP: 200
client-1  | Cuerpo JSON: {"id":"101","informacion":"Laptop Dell XPS 15 - 16GB RAM, 512GB SSD","encontrado":true}
client-1  | Peso de la respuesta (Capa de Aplicación): 87 bytes
client-1  |
client-1  | --- Prueba gRPC (ID: 101) ---
client-1  | Datos recibidos: ID=101, Info='Laptop Dell XPS 15 - 16GB RAM, 512GB SSD', Encontrado=True
client-1  | Peso de la respuesta (Capa de Aplicación): 49 bytes
client-1  |
client-1  | >>> BUSCANDO UN ID QUE NO EXISTE (999)
client-1  |
client-1  | --- Prueba REST (ID: 999) ---
client-1  | Status HTTP: 404
client-1  | Cuerpo JSON: {"id":"999","informacion":"No se encontraron datos","encontrado":false}
client-1  | Peso de la respuesta (Capa de Aplicación): 71 bytes
client-1  |
client-1  | --- Prueba gRPC (ID: 999) ---
client-1  | Datos recibidos: ID=999, Info='No se encontraron datos', Encontrado=False
client-1  | Peso de la respuesta (Capa de Aplicación): 30 bytes
client-1 exited with code 0