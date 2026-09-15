1.- Prueba de la lógica de negocio

Se prueba directamente la función obtener\_usuario() para comprobar que devuelve los datos correspondientes a un ID existente y None cuando el usuario no existe.

"""""""

>>> from service import obtener\_usuario

... print(obtener\_usuario(1))

... print(obtener\_usuario(99))

...

{'nombre': 'Mauricio', 'edad': 22}

None

>>> exit()

""""""

2.- Implementación y prueba del servicio REST
2.1.- Se inicia el servidor Flask y se comprueba que queda disponible en el puerto 5000. También se muestran las solicitudes realizadas para los IDs 1 y 99.

""""""

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\tareas\\t01\\server> python app.py

&#x20;\* Serving Flask app 'app'

&#x20;\* Debug mode: off

WARNING: This is a development server. Do not use it in a production deployment. Use a production WSGI server instead.

&#x20;\* Running on all addresses (0.0.0.0)

&#x20;\* Running on http://127.0.0.1:5000

&#x20;\* Running on http://192.168.168.102:5000

Press CTRL+C to quit

127.0.0.1 - - \[14/Sep/2026 10:50:45] "GET /usuarios/1 HTTP/1.1" 200 -

127.0.0.1 - - \[14/Sep/2026 11:02:01] "GET /usuarios/99 HTTP/1.1" 404 -

"""""""


2.2.- Se realizan solicitudes REST para un usuario existente y para un ID inexistente, comprobando las respuestas 200 y 404, respectivamente.

""""""



PS C:\\Users\\mauri> Invoke-RestMethod http://localhost:5000/usuarios/1



edad id nombre

\---- -- ------

&#x20; 22  1 Mauricio





PS C:\\Users\\mauri> Invoke-RestMethod http://localhost:5000/usuarios/99

Invoke-RestMethod : {"error":"No hay datos para el ID solicitado"}

En línea: 1 Carácter: 1

\+ Invoke-RestMethod http://localhost:5000/usuarios/99

\+ \~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~\~

&#x20;   + CategoryInfo          : InvalidOperation: (System.Net.HttpWebRequest:HttpWebRequest) \[Invoke-RestMethod], WebException

&#x20;   + FullyQualifiedErrorId : WebCmdletWebResponseException,Microsoft.PowerShell.Commands.InvokeRestMethodCommand



""""""

3.- Implementación y ejecución del servicio gRPC



Al iniciar inicialmente el servidor gRPC se presentó un error relacionado con la ubicación de los archivos generados por grpcio-tools. Se configuró PYTHONPATH para que Python pudiera localizar correctamente service\_pb2.py.

""""""


PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\tareas\\t01\\server> python grpc\_server.py

Traceback (most recent call last):

&#x20; File "C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\tareas\\t01\\server\\grpc\_server.py", line 6, in <module>

&#x20;   from proto import service\_pb2\_grpc

&#x20; File "C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\tareas\\t01\\server\\proto\\service\_pb2\_grpc.py", line 6, in <module>

&#x20;   import service\_pb2 as service\_\_pb2

ModuleNotFoundError: No module named 'service\_pb2'

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\tareas\\t01\\server> $env:PYTHONPATH = "$PWD\\proto"

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\tareas\\t01\\server> python grpc\_server.py

Servidor gRPC ejecutándose en el puerto 50051

""""""

4.- Prueba de comunicación mediante gRPC



Se ejecuta un cliente de prueba que solicita el usuario con ID 1 mediante gRPC y se comprueba que recibe correctamente sus datos.

""""""

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\tareas\\t01\\server> New-Item grpc\_test.py -ItemType File





&#x20;   Directorio:

&#x20;   C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\tareas\\t01\\server





Mode                 LastWriteTime         Length Name

\----                 -------------         ------ ----

\-a----     14/09/2026  11:47 a. m.              0 grpc\_test.py





PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\tareas\\t01\\server> $env:PYTHONPATH = "$PWD\\proto"

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\tareas\\t01\\server> python grpc\_test.py

ID: 1

Nombre: Mauricio

Edad: 22

""""""

5.- Cliente gRPC



Descripción: Se ejecuta el cliente de la práctica y se realiza una solicitud al servidor mediante gRPC para obtener los datos del usuario con ID 1.

""""""

PS C:\\Users\\mauri\\Documents\\Universidad\\SDistribuidos\\sd-2027-1\\entregas\\fernandez\_mauricio\\tareas\\t01\\client> python client.py



Respuesta gRPC:

ID: 1

Nombre: Mauricio

Edad: 22

""""""


6.- Cliente utilizando REST y gRPC

Se ejecuta el cliente y se realizan solicitudes al mismo servicio mediante REST y gRPC. Ambas interfaces devuelven los datos correspondientes al usuario con ID 1.

""""""

PS C:\Users\mauri\Documents\Universidad\SDistribuidos\sd-2027-1\entregas\fernandez_mauricio\tareas\t01\client> python client.py

Respuesta REST:
{'edad': 22, 'id': 1, 'nombre': 'Mauricio'}

Respuesta gRPC:
{'id': 1, 'nombre': 'Mauricio', 'edad': 22}

""""""

7.- Manejo de ID inexistente mediante REST y gRPC

Se consulta un ID inexistente mediante REST y gRPC. Ambas interfaces indican que no existen datos asociados al ID solicitado.

""""""

PS C:\Users\mauri\Documents\Universidad\SDistribuidos\sd-2027-1\entregas\fernandez_mauricio\tareas\t01\client> python client.py

Respuesta REST:
{'error': 'No hay datos para el ID solicitado', 'status': 404}

Respuesta gRPC:
{'error': 'No hay datos para el ID solicitado', 'status': 'NOT_FOUND'}

""""""

8.- Servidor unificado REST + gRPC

Se ejecuta server.py, que inicia simultáneamente los servicios REST y gRPC. El cliente realiza una consulta mediante ambas interfaces y obtiene los mismos datos.

""""""

PS C:\Users\mauri\Documents\Universidad\SDistribuidos\sd-2027-1\entregas\fernandez_mauricio\tareas\t01\client> python client.py
Respuesta REST:
{'edad': 22, 'id': 1, 'nombre': 'Mauricio'}

Respuesta gRPC:
{'id': 1, 'nombre': 'Mauricio', 'edad': 22}

""""""

9.-  Construcción de la imagen del servidor.

Se construye correctamente la imagen Docker del servidor a partir del Dockerfile, generando la imagen t01-server:latest.

""""""

PS C:\Users\mauri\Documents\Universidad\SDistribuidos\sd-2027-1\entregas\fernandez_mauricio\tareas\t01\server> docker build -t t01-server .
[+] Building 15.2s (10/10) FINISHED                                                                       docker:desktop-linux
 => [internal] load build definition from Dockerfile                                                                      0.0s
 => => transferring dockerfile: 258B                                                                                      0.0s
 => [internal] load metadata for docker.io/library/python:3.12-slim                                                       2.1s
 => [internal] load .dockerignore                                                                                         0.0s
 => => transferring context: 2B                                                                                           0.0s
 => [1/5] FROM docker.io/library/python:3.12-slim@sha256:78387bc3881b8273120a12ebe6c1ab22b018ccc2c9adf565ae1ac9b536e184e  4.0s
 => => resolve docker.io/library/python:3.12-slim@sha256:78387bc3881b8273120a12ebe6c1ab22b018ccc2c9adf565ae1ac9b536e184e  0.0s
 => => sha256:56235e4245636e401a28cf88944d543b29ee028ae3b6c27e03d6b43e7b4eac5f 249B / 249B                                0.1s
 => => sha256:1560cfed01dc4c72c07f789d860078d74466a1f9a26b33a2e35d887b5f90dd3f 12.12MB / 12.12MB                          3.6s
 => => sha256:dbd0b7849e6c06b61e1fa6b7ffe053f246ce0075890620e3db64b47bb662433a 4.27MB / 4.27MB                            2.7s
 => => extracting sha256:dbd0b7849e6c06b61e1fa6b7ffe053f246ce0075890620e3db64b47bb662433a                                 0.1s
 => => extracting sha256:1560cfed01dc4c72c07f789d860078d74466a1f9a26b33a2e35d887b5f90dd3f                                 0.3s
 => => extracting sha256:56235e4245636e401a28cf88944d543b29ee028ae3b6c27e03d6b43e7b4eac5f                                 0.0s
 => [internal] load build context                                                                                         0.0s
 => => transferring context: 19.56kB                                                                                      0.0s
 => [2/5] WORKDIR /app                                                                                                    0.0s
 => [3/5] COPY requirements.txt .                                                                                         0.0s
 => [4/5] RUN pip install --no-cache-dir -r requirements.txt                                                              7.0s
 => [5/5] COPY . .                                                                                                        0.0s
 => exporting to image                                                                                                    1.7s
 => => exporting layers                                                                                                   1.3s
 => => exporting manifest sha256:7ad9be1d714a1453912eb00b6fa7990b22f783aaf331d01bac91a2ccaeac8c6c                         0.0s
 => => exporting config sha256:17b6eac860adde1dd2802b963f977cf16c1c2e453b3e33cd73e81bda25abc839                           0.0s
 => => exporting attestation manifest sha256:37a3bbf06d36c62d336be1797ef043704af48c02984a3bc4f91cae7511c1393c             0.0s
 => => exporting manifest list sha256:fcef9d422fbf61222ecb7ef5bc4227baa74413dad89a06f65a23e38f9c78e480                    0.0s
 => => naming to docker.io/library/t01-server:latest                                                                      0.0s
 => => unpacking to docker.io/library/t01-server:latest                                                                   0.3s

""""""

10.- Ejecución del servidor en Docker

Se ejecuta el servidor unificado dentro de un contenedor Docker, exponiendo los puertos correspondientes a los servicios REST y gRPC.

""""""

PS C:\Users\mauri\Documents\Universidad\SDistribuidos\sd-2027-1\entregas\fernandez_mauricio\tareas\t01\server> docker run --name t01-server -p 5000:5000 -p 50051:50051 t01-server
 * Serving Flask app 'app'
 * Debug mode: off
WARNING: This is a development server. Do not use it in a production deployment. Use a production WSGI server instead.
 * Running on all addresses (0.0.0.0)
 * Running on http://127.0.0.1:5000
 * Running on http://172.17.0.2:5000
Press CTRL+C to quit

""""""

11.- Cliente contra servidor Dockerizado

""""""

PS C:\Users\mauri\Documents\Universidad\SDistribuidos\sd-2027-1\entregas\fernandez_mauricio\tareas\t01\client> python client.py
Respuesta REST:
{'edad': 22, 'id': 1, 'nombre': 'Mauricio'}

Respuesta gRPC:
{'id': 1, 'nombre': 'Mauricio', 'edad': 22}

""""""

12.- Construcción de la imagen del cliente

""""""

PS C:\Users\mauri\Documents\Universidad\SDistribuidos\sd-2027-1\entregas\fernandez_mauricio\tareas\t01\client> docker build -t t01-client .
[+] Building 9.4s (10/10) FINISHED                                                                        docker:desktop-linux
 => [internal] load build definition from Dockerfile                                                                      0.0s
 => => transferring dockerfile: 200B                                                                                      0.0s
 => [internal] load metadata for docker.io/library/python:3.12-slim                                                       1.0s
 => [internal] load .dockerignore                                                                                         0.0s
 => => transferring context: 2B                                                                                           0.0s
 => [1/5] FROM docker.io/library/python:3.12-slim@sha256:78387bc3881b8273120a12ebe6c1ab22b018ccc2c9adf565ae1ac9b536e184e  0.0s
 => => resolve docker.io/library/python:3.12-slim@sha256:78387bc3881b8273120a12ebe6c1ab22b018ccc2c9adf565ae1ac9b536e184e  0.0s
 => [internal] load build context                                                                                         0.0s
 => => transferring context: 13.54kB                                                                                      0.0s
 => CACHED [2/5] WORKDIR /app                                                                                             0.0s
 => [3/5] COPY requirements.txt .                                                                                         0.0s
 => [4/5] RUN pip install --no-cache-dir -r requirements.txt                                                              6.4s
 => [5/5] COPY . .                                                                                                        0.0s
 => exporting to image                                                                                                    1.7s
 => => exporting layers                                                                                                   1.3s
 => => exporting manifest sha256:ef1b7f3d7bb8bae93425a64a79440e45141007cbe3bd7091b250f85f559ef9d0                         0.0s
 => => exporting config sha256:d06bf54f0e54e8900b0a0684986e0d24fa9bef4e6fc55ab7ffec615985e20d40                           0.0s
 => => exporting attestation manifest sha256:ec3f116c5f422e00eb35de138f036bddec669d3462bef39acd50a61bec118932             0.0s
 => => exporting manifest list sha256:a3fd358db1798cab1f1bf72fc1efd317d2384ace21fab5fa8fb945ddd19f9803                    0.0s
 => => naming to docker.io/library/t01-client:latest                                                                      0.0s
 => => unpacking to docker.io/library/t01-client:latest     

""""""

13.- Ejecución de cliente y servidor con Docker Compose

Se ejecutan conjuntamente el servidor y el cliente mediante Docker Compose. El cliente se comunica con el servidor a través de la red de Docker y obtiene correctamente los datos del usuario mediante REST y gRPC.

"""""""

PS C:\Users\mauri\Documents\Universidad\SDistribuidos\sd-2027-1\entregas\fernandez_mauricio\tareas\t01> docker compose up
[+] up 3/3
 ✔ Network t01_default  Created                                                                                                           0.0s
 ✔ Container t01-server Created                                                                                                           0.1s
 ✔ Container t01-client Created                                                                                                           0.1s
Attaching to t01-client, t01-server
t01-server  |  * Serving Flask app 'app'
t01-server  |  * Debug mode: off
t01-server  | WARNING: This is a development server. Do not use it in a production deployment. Use a production WSGI server instead.
t01-server  |  * Running on all addresses (0.0.0.0)
t01-server  |  * Running on http://127.0.0.1:5000
t01-server  |  * Running on http://172.20.0.2:5000
t01-server  | Press CTRL+C to quit
t01-server  | 172.20.0.3 - - [15/Sep/2026 03:07:05] "GET /usuarios/1 HTTP/1.1" 200 -
t01-client  | Respuesta REST:
t01-client  | {'edad': 22, 'id': 1, 'nombre': 'Mauricio'}
t01-client  |
t01-client  | Respuesta gRPC:
t01-client  | {'id': 1, 'nombre': 'Mauricio', 'edad': 22}
t01-client exited with code 0

""""""

14.- Comparación de bytes

Se mide el tamaño del contenido recibido para una misma consulta mediante REST y gRPC. La respuesta REST tiene un tamaño de 39 bytes, mientras que la respuesta gRPC serializada mediante Protocol Buffers ocupa 14 bytes. En esta prueba, gRPC utiliza 25 bytes menos, debido a que Protocol Buffers representa los datos de forma binaria y compacta.

""""""

PS C:\Users\mauri\Documents\Universidad\SDistribuidos\sd-2027-1\entregas\fernandez_mauricio\tareas\t01> python medir_bytes.py
REST
Respuesta: {'edad': 22, 'id': 1, 'nombre': 'Mauricio'}
Bytes recibidos: 39

gRPC
Respuesta: {'id': 1, 'nombre': 'Mauricio', 'edad': 22}
Bytes recibidos: 14

""""""