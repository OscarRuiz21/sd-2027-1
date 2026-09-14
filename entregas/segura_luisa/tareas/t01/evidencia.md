\# Evidencia de Tarea · T01

\*\*Nombre:\*\* Luisa María Segura Cedeño  

\*\*Fecha:\*\* 13 de septiembre de 2026  



\---



\## 1. Contrato de Comunicación (gRPC Protobuf)



Definición formal del servicio e interfaz de datos compartida (`proto/servicio.proto`).



<pre><code>syntax = "proto3";



package servicio;



message ItemRequest {

&#x20; string id = 1;

}



message ItemResponse {

&#x20; string id = 1;

&#x20; string nombre = 2;

&#x20; string detalle = 3;

&#x20; bool encontrado = 4;

}



service ItemService {

&#x20; rpc GetItem (ItemRequest) returns (ItemResponse);

}</code></pre>



\---



\## 2. Salida de Ejecución: Pruebas del Cliente



Demostración de que el cliente consulta al mismo servidor y a la misma fuente de verdad tanto por REST como por gRPC.



<pre><code>$ docker compose up --build

\[+] Building 0.0s (0/0)

\[+] Running 2/2

&#x20;✔ Container t01-servidor-1  Created

&#x20;✔ Container t01-cliente-1   Created

Attaching to cliente-1, servidor-1



cliente-1   | ========================================

cliente-1   |  INICIANDO PRUEBAS DE COMUNICACION 

cliente-1   | ========================================

cliente-1   | 

cliente-1   | --- \[REST] Consultando ID: 1 ---

cliente-1   | Status Code: 200

cliente-1   | Respuesta: {"id":"1","nombre":"Libro de Redes","detalle":"Tanenbaum - Cap 1","encontrado":true}

cliente-1   | Bytes en payload: 86 bytes (Encabezados: \~155 bytes)

cliente-1   | 

cliente-1   | --- \[gRPC] Consultando ID: 1 ---

cliente-1   | Respuesta gRPC: id='1', nombre='Libro de Redes', detalle='Tanenbaum - Cap 1'

cliente-1   | Bytes en payload Protobuf: 42 bytes

cliente-1   | 

cliente-1   | --- \[REST] Consultando ID: 99 ---

cliente-1   | Status Code: 404

cliente-1   | Respuesta: {"detail":"Item no encontrado"}

cliente-1   | Bytes en payload: 31 bytes (Encabezados: \~155 bytes)

cliente-1   | 

cliente-1   | --- \[gRPC] Consultando ID: 99 ---

cliente-1   | gRPC Status: StatusCode.NOT\_FOUND | Detalle: Item no encontrado

cliente-1   | 

cliente-1   | ========================================

cliente-1   |  PRUEBAS FINALIZADAS CON EXITO 

cliente-1   | ========================================

cliente-1 exited with code 0</code></pre>



\---



\## 3. Verificación de Contenedores y Red



<pre><code>$ docker compose ps -a

NAME             IMAGE          COMMAND                  SERVICE    CREATED         STATUS                     PORTS

t01-servidor-1   t01-servidor   "python main.py"         servidor   2 minutes ago   Up 2 minutes               0.0.0.0:8000->8000/tcp, 0.0.0.0:50051->50051/tcp

t01-cliente-1    t01-cliente    "python client.py"       cliente    2 minutes ago   Exited (0) 2 minutes ago   </code></pre>

