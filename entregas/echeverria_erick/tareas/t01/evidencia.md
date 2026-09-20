# Evidencia Tarea 1

**Alumno:** Echeverria Goicochea Erick Isaac

## 1. Contenedores corriendo
Después de construir las imágenes y levantar los servicios con Docker Compose:
```bash
docker build -t t01-servidor ./server
docker build -t t01-cliente ./client
docker compose up -d
```
Se comprobó que los dos contenedores fueron creados:
```shell
┌──(venv)(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/tareas/t01]
└─$ docker compose ps -a
NAME             IMAGE          COMMAND                  SERVICE    CREATED         STATUS                     PORTS
t01-cliente-1    t01-cliente    "python3 cliente.py …"   cliente    2 minutes ago   Exited (0) 2 minutes ago
t01-servidor-1   t01-servidor   "python3 index.py"       servidor   7 minutes ago   Up 7 minutes               0.0.0.0:3000->3000/tcp, [::]:
3000->3000/tcp, 0.0.0.0:50051->50051/tcp, [::]:50051->50051/tcp
```
El cliente termina con `Exited (0)` porque realiza las llamadas a REST y gRPC y después termina su ejecución. El servidor permanece ejecutándose para atender las peticiones.

## 2. Servidor con REST y gRPC

Los logs del servidor muestran que se inició correctamente:

```shell
┌──(venv)(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/tareas/t01]
└─$ docker compose logs servidor
servidor-1  |  * Serving Flask app 'rest'
servidor-1  |  * Debug mode: off
servidor-1  | WARNING: This is a development server. Do not use it in a production deployment. Use a production WSGI server instead.
servidor-1  |  * Running on all addresses (0.0.0.0)
servidor-1  |  * Running on http://127.0.0.1:3000
servidor-1  |  * Running on http://172.19.0.2:3000
servidor-1  | Press CTRL+C to quit
servidor-1  | 172.19.0.3 - - [18/Sep/2026 05:33:18] "GET /item/1 HTTP/1.1" 200 -
```

El servidor también inicia el servicio gRPC en el puerto `50051`.

## 3. Cliente llamando a REST y gRPC

El cliente realiza una llamada por cada protocolo utilizando el mismo ID:

```shell
┌──(venv)(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/tareas/t01]
└─$ docker compose logs cliente
cliente-1  | REST responde: {'area': 'Computacion', 'encontrado': True, 'nombre': 'Pedro Lopez'}
cliente-1  | gRPC responde: encontrado: true
cliente-1  | nombre: "Pedro Lopez"
cliente-1  | area: "Computacion"
```

Esto demuestra que las dos interfaces llegan a la misma lógica de búsqueda y obtienen la información correspondiente al ID `1`.

## 4. Prueba de REST desde fuera de Docker

Se probó un ID existente:

```shell
┌──(venv)(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/tareas/t01]
└─$ curl http://localhost:3000/item/1
{"area":"Computacion","encontrado":true,"nombre":"Pedro Lopez"}
```

También se probó un ID que no existe:

```shell
┌──(venv)(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/tareas/t01]
└─$ curl http://localhost:3000/item/10
{"encontrado":false,"mensaje":"No hay datos para el ID ingresado"}
```

En ambos casos REST devuelve la respuesta correspondiente.

## 5. Prueba de gRPC

El cliente ya mostró una llamada gRPC exitosa para el ID `1`:

```shell
gRPC responde: encontrado: true
nombre: "Pedro Lopez"
area: "Computacion"
```

En el caso en que el ID no existe, se puede ejecutar nuevamente el cliente indicando el ID `10`:

```bash
┌──(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/tareas/t01]
└─$ docker compose run --rm cliente python3 cliente.py 10 servidor
[+] Creating 1/1
 ✔ Container t01-servidor-1  Running                                                                                                   0.0s 
REST responde: {'encontrado': False, 'mensaje': 'No hay datos para el ID ingresado'}
gRPC responde: mensaje: "No hay datos para el ID ingresado"
```

Esta prueba sirve para comprobar que gRPC utiliza la misma lógica que REST también cuando el ID no existe.

## 6. Red de Docker

Se comprobó que Compose creó una red para los servicios:

```shell
┌──(venv)(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/tareas/t01]
└─$ docker network ls
NETWORK ID     NAME          DRIVER    SCOPE
799b7fd3425e   bridge        bridge    local
c5d34c8ea194   host          host      local
fce1257b9a22   none          null      local
0df65b1fa7f7   t01_default   bridge    local
```

También se comprobó la dirección interna del servidor:

```shell
┌──(venv)(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/tareas/t01]
└─$ docker compose exec servidor hostname -i
172.19.0.2
```

Aunque el servidor tiene esa dirección IP dentro de la red, el cliente no depende de ella. Se comunica utilizando el nombre del servicio: `Servidor`

Esto permite que Docker Compose resuelva automáticamente el contenedor correspondiente.

## 7. Medición de bytes

### REST

Se utilizó `curl` con la opción `-w`:

```shell
┌──(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/tareas/t01]
└─$ curl -w "\nBytes descargados: %{size_download}\n" http://localhost:3000/item/1
{"area":"Computacion","encontrado":true,"nombre":"Pedro Lopez"}
Bytes descargados: 64
```

El resultado fue: `64 bytes`


Esta medición corresponde al tamaño de la respuesta descargada por `curl`.

### gRPC

Para gRPC se utilizó el método `SerializeToString()` de Protobuf:

```text
┌──(venv)(erick㉿pc-bd-eeg)-[~/sd-2027-1/entregas/echeverria_erick/tareas/t01/server]
└─$ python3 medir_grpc.py
Bytes del mensaje gRPC: 28
```

El resultado fue `28 bytes`

### Cómo se hizo la comparación

Los valores 64 y 28 bytes corresponden al tamaño del contenido de la respuesta en REST y del mensaje serializado en gRPC, respectivamente. No incluyen todos los elementos del transporte de red.

La diferencia se debe principalmente a que JSON utiliza texto y nombres de campos, mientras que Protobuf utiliza una representación binaria. Por eso el mensaje gRPC es más pequeño.
