# Evidencia T01 — El mismo servicio, por REST y gRPC

**Alumno:** Diego Alexander Carrasco Quiñones

## 1. Estructura del proyecto

Primero se verificó la estructura de la carpeta de la tarea. La terminal se encontraba en:

```text
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01>
```

Se ejecutó:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01> dir
```

Resultado:

```text
    Directorio: C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01

Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d-----     20/09/2026  04:28 p. m.                client
d-----     16/09/2026  09:41 p. m.                proto
d-----     20/09/2026  10:35 a. m.                server
-a----     20/09/2026  04:21 p. m.            596 compose.yaml
-a----     20/09/2026  05:10 p. m.          20104 README.md
```

La carpeta contiene los directorios `client`, `proto` y `server`, además de `compose.yaml` y `README.md`. Esto permite comprobar que los componentes principales de la tarea están separados correctamente.

## 2. Archivos del servidor

Se entró a la carpeta `server`:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01> cd server
```

Después se ejecutó:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01\server> dir
```

Resultado:

```text
    Directorio: C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01\server

Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d-----     20/09/2026  10:35 a. m.                generated
-a----     20/09/2026  10:20 a. m.             ... Dockerfile
-a----     20/09/2026  10:15 a. m.             ... grpc_server.py
-a----     20/09/2026  10:18 a. m.             ... main.py
-a----     20/09/2026  10:10 a. m.             ... requirements.txt
-a----     20/09/2026  10:05 a. m.             ... rest.py
-a----     20/09/2026  10:00 a. m.             ... service.py
```

Aquí se encuentran los archivos encargados de la lógica de negocio, REST, gRPC y la configuración necesaria para ejecutar el servidor.

## 3. Prueba de la lógica de negocio

Se regresó a la carpeta principal:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01\server> cd ..
```

Se ejecutó directamente la función que realiza la búsqueda:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01> python -c "from server.service import buscar_por_id; print(buscar_por_id(1)); print(buscar_por_id(99))"
```

Resultado:

```text
{'id': 1, 'nombre': 'Alex'}
None
```

El ID `1` existe y devuelve la información de Alex. El ID `99` no existe y devuelve `None`. Esta función es la lógica de negocio que posteriormente utilizan tanto REST como gRPC.

## 4. Archivo `.proto`

Se revisó la carpeta `proto`:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01> cd proto
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01\proto> dir
```

Resultado:

```text
    Directorio: C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01\proto

Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
-a----     16/09/2026  09:41 p. m.            ... servicio.proto
```

El archivo `servicio.proto` define el contrato utilizado por gRPC.

Su contenido es:

```protobuf
syntax = "proto3";

package personas;

service PersonaService {
    rpc ObtenerPersona (PersonaRequest) returns (PersonaResponse);
}

message PersonaRequest {
    int32 id = 1;
}

message PersonaResponse {
    int32 id = 1;
    string nombre = 2;
    string mensaje = 3;
}
```

El servicio define una operación `ObtenerPersona`, que recibe un ID y devuelve la información de la persona.

## 5. Prueba del servidor REST y gRPC

Se regresó a la carpeta principal:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01\proto> cd ..
```

Se inició el servidor:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01> python -m server.main
```

Resultado:

```text
Servidor REST iniciado en el puerto 8000
Servidor gRPC iniciado en el puerto 50051
INFO:     Uvicorn running on http://0.0.0.0:8000
```

Esto demuestra que el programa principal inicia los dos servicios al mismo tiempo. REST utiliza el puerto `8000` y gRPC utiliza el puerto `50051`.

## 6. Prueba del servicio REST

Con el servidor ejecutándose se realizó una petición al endpoint:

```text
http://localhost:8000/personas/1
```

La respuesta fue:

```text
{"id":1,"nombre":"Alex"}
```

También se probó un ID inexistente:

```text
http://localhost:8000/personas/99
```

La respuesta fue:

```text
{"mensaje":"No hay datos"}
```

Esto demuestra que el servicio REST puede encontrar una persona y también manejar correctamente un ID que no existe.

## 7. Documentación de REST

FastAPI proporciona automáticamente una interfaz de documentación.

Se accedió desde el navegador a:

```text
http://localhost:8000/docs
```

Se mostró el endpoint:

```text
GET /personas/{id}
```

La página permitió comprobar que el servicio REST estaba funcionando correctamente.

## 8. Prueba del cliente

Después de comprobar el servidor, se detuvo el proceso y se ejecutó el cliente desde la carpeta principal:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01> python client\cliente.py
```

Resultado:

```text
Consulta mediante REST:
{'id': 1, 'nombre': 'Alex'}

Consulta mediante gRPC:
id: 1, nombre: Alex
```

El cliente obtuvo la misma persona utilizando REST y gRPC. Esto demuestra que ambos mecanismos están conectados al mismo servicio y utilizan la misma lógica de búsqueda.

También se probó el ID `99`:

```text
Consulta mediante REST:
{'mensaje': 'No hay datos'}

Consulta mediante gRPC:
No hay datos
```

Los dos métodos producen el resultado esperado cuando la persona no existe.

## 9. Construcción de la imagen Docker del servidor

Desde la carpeta principal se ejecutó:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01> docker build -t t01-servidor -f server\Dockerfile .
```

Resultado:

```text
[+] Building ...
...
=> exporting to image
=> naming to docker.io/library/t01-servidor
```

La imagen del servidor fue construida correctamente utilizando el `Dockerfile` ubicado en la carpeta `server`.

## 10. Problema con un contenedor anterior

Durante las pruebas existía un contenedor llamado `t01-servidor` que ocupaba los puertos necesarios. Se eliminó utilizando:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01> docker rm -f t01-servidor
```

Resultado:

```text
t01-servidor
```

Después se comprobó que no hubiera contenedores ejecutándose:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01> docker ps
```

Resultado:

```text
CONTAINER ID   IMAGE   COMMAND   CREATED   STATUS   PORTS   NAMES
```

El contenedor anterior fue eliminado para liberar los puertos utilizados por el proyecto.

## 11. Construcción con Docker Compose

Desde la carpeta `t01` se ejecutó:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01> docker compose build
```

Resultado:

```text
[+] build 2/2
 ✔ Image t01-server Built
 ✔ Image t01-client Built
```

Docker Compose construyó correctamente las imágenes del servidor y del cliente.

## 12. Configuración de los servicios

Se verificó la configuración de Compose:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01> docker compose config
```

Entre la configuración obtenida se encontraron:

```text
services:
  client:
    environment:
      SERVIDOR: server
    depends_on:
      server:
        condition: service_healthy

  server:
    ports:
      - target: 8000
        published: 8000
      - target: 50051
        published: 50051
```

La variable `SERVIDOR=server` permite que el cliente utilice el nombre del servicio de Docker Compose para comunicarse con el servidor.

## 13. Problema de inicio del cliente

En una primera ejecución de Compose, el cliente intentó realizar la petición antes de que el servidor estuviera listo.

La terminal mostró:

```text
ConnectionRefusedError: [Errno 111] Connection refused
HTTPConnectionPool(host='server', port=8000)...
```

El problema se produjo porque el contenedor del cliente comenzó a ejecutarse antes de que el servidor terminara de iniciar.

Para solucionarlo se agregó un `healthcheck` al servidor y se configuró el cliente para esperar a que el servidor estuviera saludable.

## 14. Healthcheck

El servidor utiliza:

```yaml
healthcheck:
  test: ["CMD", "python", "-c", "import urllib.request; urllib.request.urlopen('http://localhost:8000/personas/1')"]
  interval: 5s
  timeout: 3s
  retries: 5
```

El cliente utiliza:

```yaml
depends_on:
  server:
    condition: service_healthy
```

De esta forma, Compose espera a que el servidor pueda responder correctamente antes de iniciar el cliente.

## 15. Ejecución final con Docker Compose

Después de corregir el problema se ejecutó:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01> docker compose up
```

Resultado:

```text
[+] up 3/3
 ✔ Network t01_default Created
 ✔ Container t01-server Created
 ✔ Container t01-client Created
Attaching to t01-client, t01-server
Container t01-server Waiting
```

El servidor inició correctamente:

```text
t01-server  | INFO: Started server process [1]
t01-server  | INFO: Application startup complete.
t01-server  | INFO: Uvicorn running on http://0.0.0.0:8000
t01-server  | Servidor REST iniciado en el puerto 8000
t01-server  | Servidor gRPC iniciado en el puerto 50051
```

Posteriormente el `healthcheck` comprobó que REST respondía:

```text
t01-server  | INFO: 127.0.0.1:59230 - "GET /personas/1 HTTP/1.1" 200 OK
Container t01-server Healthy
```

Una vez que el servidor estuvo saludable, se inició el cliente:

```text
t01-client  | Consulta mediante REST:
t01-client  | {'id': 1, 'nombre': 'Alex'}

t01-client  | Consulta mediante gRPC:
t01-client  | id: 1, nombre: Alex
t01-client exited with code 0
```

Esta es la prueba principal de la práctica. El servidor y el cliente funcionaron en contenedores separados y el cliente pudo comunicarse con el servidor utilizando REST y gRPC.

## 16. Red de Docker Compose

Durante la ejecución se creó automáticamente la red:

```text
t01_default
```

Esta red permite que los servicios de Compose se comuniquen entre sí.

El cliente utiliza:

```text
server:8000
```

para REST y:

```text
server:50051
```

para gRPC.

No fue necesario utilizar una dirección IP fija, ya que Docker Compose proporciona resolución de nombres mediante el nombre del servicio.

## 17. Detención del proyecto

Después de terminar las pruebas se ejecutó:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01> docker compose down
```

Resultado:

```text
[+] down 3/3
 ✔ Container t01-client Removed
 ✔ Container t01-server Removed
 ✔ Network t01_default Removed
```

Esto eliminó los contenedores y la red creada por Compose.

## 18. Medición de bytes

Como prueba adicional se utilizó el programa `client\medir_bytes.py`.

Primero se inició el servidor:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01> docker compose up -d server
```

Después se comprobó:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01> docker ps
```

Resultado:

```text
CONTAINER ID   IMAGE        COMMAND                  STATUS                  PORTS
...            t01-server   ...                      Up ... (healthy)       0.0.0.0:8000->8000/tcp
                                                                            0.0.0.0:50051->50051/tcp
```

El servidor se encontraba funcionando y saludable.

Después se ejecutó:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01> python client\medir_bytes.py
```

Resultado:

```text
REST
Respuesta: {"id":1,"nombre":"Alex"}
Bytes de respuesta: 24

gRPC
Respuesta: id: 1
nombre: "Alex"

Bytes de solicitud: 2
Bytes de respuesta: 8
Bytes totales de mensajes: 10
```

La prueba muestra que el contenido de la respuesta REST ocupó 24 bytes, mientras que los mensajes protobuf utilizados por gRPC ocuparon 2 bytes para la solicitud y 8 bytes para la respuesta.

Esta medición corresponde únicamente al contenido de los mensajes de aplicación. No representa todo el tráfico de red, ya que no incluye encabezados HTTP, HTTP/2, TCP/IP ni otros datos de transporte.

## 19. Comprobación final

Finalmente se comprobó que los principales elementos de la tarea están presentes:

```powershell
PS C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01> dir
```

Resultado:

```text
    Directorio: C:\Users\eluve\repos\sd-2027-1\entregas\carrasco_alexander\tareas\t01

Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d-----     20/09/2026  04:28 p. m.                client
d-----     16/09/2026  09:41 p. m.                proto
d-----     20/09/2026  10:35 a. m.                server
-a----     20/09/2026  04:21 p. m.            596 compose.yaml
-a----     20/09/2026  05:10 p. m.          20104 README.md
```

La carpeta contiene los tres componentes principales del proyecto: `client`, `proto` y `server`, además del archivo `compose.yaml` y la documentación `README.md`.

## 20. Resultado general

Con las pruebas realizadas se comprobó que:

```text
✔ La lógica de negocio funciona.
✔ REST responde correctamente.
✔ gRPC responde correctamente.
✔ REST y gRPC utilizan la misma lógica de negocio.
✔ El archivo .proto define el contrato gRPC.
✔ El cliente puede utilizar ambos protocolos.
✔ El servidor puede ejecutarse dentro de Docker.
✔ El cliente puede ejecutarse dentro de Docker.
✔ Docker Compose crea la red interna.
✔ El cliente puede comunicarse con el servidor mediante el nombre "server".
✔ El healthcheck evita que el cliente inicie antes que el servidor.
✔ La medición de bytes permite comparar los mensajes de aplicación.
```

## Conclusión

Ya vimos que el servicio funcionó correctamente y que fue posible realizar las mismas consultas utilizando tanto REST como gRPC. Esta tarea fue una experiencia muy enriquecedora para mí, ya que nunca había realizado algo parecido y al principio no tenía muy claro cómo se relacionaban todas las partes del proyecto.

Tengo que reconocer que utilicé inteligencia artificial como apoyo durante el desarrollo de la tarea, principalmente para poder entender algunos comandos, resolver los problemas que fueron apareciendo, obtener orientación sobre el diseño e implementación de la arquitectura y comprender mejor algunos conceptos que todavía no dominaba. Sin embargo, durante el proceso fui revisando las soluciones y entendiendo qué hacía cada parte, lo que me permitió comprender mejor el funcionamiento del proyecto.

Con esta tarea pude observar directamente cómo funciona REST, donde normalmente los datos se intercambian utilizando formatos como JSON, y cómo gRPC utiliza Protocol Buffers para definir y serializar los mensajes. También pude entender que cada tecnología tiene características diferentes: REST es sencillo de utilizar, fácil de probar y muy común en aplicaciones web, mientras que gRPC utiliza un formato binario y un contrato definido mediante archivos .proto, lo que resulta útil para la comunicación entre servicios.

Lo más importante que pude aprender es que no necesariamente se tiene que elegir solamente uno de los dos. En una misma arquitectura pueden utilizarse REST y gRPC al mismo tiempo, dependiendo de las necesidades de cada parte del sistema. En esta tarea ambos protocolos utilizaron la misma lógica de negocio, demostrando que es posible ofrecer diferentes formas de comunicación para un mismo servicio.

En general, considero que la tarea me ayudó a perder un poco el miedo a trabajar con Docker, redes, servicios y diferentes protocolos de comunicación. Aunque necesité apoyo de herramientas de IA para avanzar y solucionar algunos problemas, también pude aprender de los errores y entender mejor cómo funcionan REST, gRPC y Docker Compose en conjunto.
