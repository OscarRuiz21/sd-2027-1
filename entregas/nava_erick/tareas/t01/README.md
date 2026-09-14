# Comparación de Rendimiento: REST vs gRPC

## Instrucciones de Ejecución

Para levantar el proyecro se deben de ejecutar los comandos desde el directorio `t01`. Se usarán tres terminales simultaneas.

## Diseño:
t01/
├── cliente
│   ├── cliente.js
│   └── package.json
│   └── Dockerfile
│
├── servidor/
│   ├── Dockerfile
│   ├── logica.js
│   ├── package-lock.json
│   ├── package.json
│   └── servidor.js
│
├── api.proto
├── docker-compose.yml
└── README.md

### creación de entronos
Tanto en el directorio cliente/ y en el directorio servidor sera necesario la creacion de un entorno de desarrollo con los compandos 
```bash
# En la carpeta del servidor
cd servidor
npm init -y
npm install express @grpc/grpc-js @grpc/proto-loader

# En la carpeta del cliente
cd ../cliente
npm init -y
npm install @grpc/grpc-js @grpc/proto-loader axios
```

### Terminal 1: Iniciar los contenedores

En la primera terminal se levantan el servidor y la infraestructura:

```bash
docker compose up --build
```

Esta terminal debe permanecer ejecutándose.

### Terminal 2: Monitor de Red con tcpdump

En la segunda terminal se entra al contenedor del cliente de forma interactiva:

```bash
docker compose exec cliente sh
```

Una vez dentro del contenedor, se utiliza `tcpdump` dependiendo del protocolo que se quiera analizar.

#### REST

```bash
tcpdump -i any port 8080 -X
```

#### gRPC

```bash
tcpdump -i any port 50051 -X
```

### Terminal 3: Ejecución del Cliente

En la tercer temrinal se ejeutará el cliente:

```bash
docker compose exec cliente node cliente.js
```

---

## Salida del Cliente

Al ejecutar el cliente se realizan consultas utilizando ambos protocolos: REST y gRPC.

La primera prueba consulta un ID existente (`1`), mientras que la segunda consulta un ID que no existe (`99`).

```text
Iniciando cliente server
==== ID existente =====

[REST] Buscando ID 1
[REST] Respuesta recibida: { success: true, message: 'Dato encontado', data: 'Juan Carlos ' }

[gRPC] Buscando ID 1
[gRPC] Respuesta recibida: { success: true, message: 'Dato encontado', data: 'Juan Carlos ' }

==== ID NO existente =====

[REST] Buscando ID 99
[REST] Respuesta recibida: { success: false, message: 'Dato NO encontrado', data: null }

[gRPC] Buscando ID 99
[gRPC] Respuesta recibida: { success: false, message: 'Dato NO encontrado', data: '' }
```

---

## Análisis del Tráfico de Red

Mediante `tcpdump` se capturaron los paquetes enviados por REST y gRPC para comparar principalmente:

* Tamaño de los paquetes.
* Formato de los datos.
* Legibilidad del contenido.
* Estructura de la comunicación.

### 1. REST — Puerto 8080

REST utiliza **HTTP/1.1** y transmite la información en formato JSON. Es facil de interpretarse ya que las cabeceras y el cuerpo son legibles

En la prueba realizada, la respuesta tuvo un tamaño de aproximadamente **300 bytes**.

```text
03:47:59.414132 eth0  In  ... length 300: HTTP: HTTP/1.1 200 OK
        ...
        0x0110:  3a20 7469 6d65 6f75 743d 350d 0a0d 0a7b  :.timeout=5....{
        0x0120:  2273 7563 6365 7373 223a 7472 7565 2c22  "success":true,"
        0x0130:  6d65 7373 6167 6522 3a22 4461 746f 2065  message":"Dato.e
        0x0140:  6e63 6f6e 7461 646f 222c 2264 6174 6122  ncontado","data"
        0x0150:  3a22 4a75 616e 2043 6172 6c6f 7320 227d  :"Juan.Carlos."}
```

En la captura se pueden identificar directamente elementos de la respuesta JSON, por ejemplo

---

### 2. gRPC — Puerto 50051

gRPC utiliza **HTTP/2** y serializa los mensajes.

A diferencia de REST, los datos no se transmiten como un documento JSON legible directamente. Los campos son representados mediante una estructura binaria.

En la prueba realizada, la respuesta tuvo un tamaño de aproximadamente **150 bytes**.

#### Fragmento de la captura

```text
03:48:34.497003 eth0  In  ... length 150
        ...
        0x0090:  0271 4033 71a7 ae32 d298 b46f 0000 2500  .q@3q..2...o..%.
        0x00a0:  0000 0000 0100 0000 0020 0801 120e 4461  ..............Da
        0x00b0:  746f 2065 6e63 6f6e 7461 646f 1a0c 4a75  to.encontado..Ju
        0x00c0:  616e 2043 6172 6c6f 7320                 an.Carlos.
```

Aunque la información se encuentra codificada en formato binario, todavía es posible identificar algunas cadenas de texto dentro de la captura, como:

```text
Dato encontado
Juan Carlos
```

---

## Comparación

| Característica      | REST                       | gRPC             |
| ------------------- | -------------------------- | ---------------- |
| Protocolo           | HTTP/1.1                   | HTTP/2           |
| Serialización       | JSON                       | Protocol Buffers |
| Formato             | Texto                      | Binario          |
| Legibilidad directa | Alta                       | Baja             |
| Tamaño observado    | ~300 bytes                 | ~150 bytes       |
| Estructura de datos | JSON con nombres de campos | Mensaje binario  |

### Resultado

En la prueba realizada, gRPC utilizó cerca de la mitad del tamaño del que usó REST

* **REST:** ~300 bytes
* **gRPC:** ~150 bytes

Esto se debe principalmente a que REST transmite información en formato JSON junto con las cabeceras de HTTP/1.1, mientras que gRPC utiliza Protocol Buffers y HTTP/2, lo que permite una representación más compacta de los datos.
