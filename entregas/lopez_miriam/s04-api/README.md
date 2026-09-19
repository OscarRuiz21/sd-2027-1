# S04 - REST vs gRPC

## Descripción

En esta tarea implementé el mismo servicio de dos formas diferentes
En una versión use REST 
En otra version use gRPC

El servicio que realicé es una suma de dos números. En ambos casos se reciben dos valores, `a` y `b`, y se devuelve el resultado de la suma.

Por ejemplo:
Si mis valores son los siguientes
a = 10
b = 5
El resultado sera
resultado = 15

---

## REST

Para REST usé Python con Flask

El endpoint que hice fue:

```text
POST /sumar
```

Recibe algo como esto

```json
{
  "a": 10,
  "b": 5
}
```

Y responde

```json
{
  "resultado": 15.0
}
```

También agregué un error por si no se mandan los dos números

Si todo sale bien responde con

```text
200 OK
```

Si falta algún dato responde con

```text
400 Bad Request
```

Por ejemplo, si mando solamente

```json
{
  "a": 10
}
```

regresa

```json
{
  "error": "Se deben enviar los valores a y b"
}
```

### Prueba de REST

Para probarlo usé

```powershell
curl.exe -X POST http://localhost:5001/sumar -H "Content-Type: application/json" -d '{\"a\":10,\"b\":5}'
```

Y obtuve

```json
{"resultado":15.0}
```

---

## gRPC

Para gRPC también usé Python

Primero hice el archivo `calculator.proto`, donde se define el servicio

```proto
syntax = "proto3";

package calculator;

service Calculator {
  rpc Sumar (SumaRequest) returns (SumaResponse);
}

message SumaRequest {
  double a = 1;
  double b = 2;
}

message SumaResponse {
  double resultado = 1;
}
```

Después generé estos archivos

```text
calculator_pb2.py
calculator_pb2_grpc.py
```

Usé este comando

```powershell
py -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. calculator.proto
```

### Prueba de gRPC

Primero revisé que el servicio estuviera disponible con:

```powershell
grpcurl -plaintext localhost:50052 list
```

Me apareció:

```text
calculator.Calculator
grpc.reflection.v1alpha.ServerReflection
```

Después probé la suma con:

```powershell
'{"a":10,"b":5}' | grpcurl -plaintext -d '@' localhost:50052 calculator.Calculator/Sumar
```

El resultado fue:

```json
{
  "resultado": 15
}
```

Entonces las dos versiones hicieron lo mismo:

```text
REST = 15
gRPC = 15
```

---

## Docker

Hice un `Dockerfile` para REST y otro para gRPC. También hice un archivo `docker-compose.yml` para levantar los dos servicios al mismo tiempo.

Para iniciarlos usé:

```powershell
docker compose up -d
```

Y para revisar que estuvieran funcionando

```powershell
docker compose ps
```

Los puertos que usé fueron

```text
REST: 5001
gRPC: 50052
```

Para detener los servicios use

```powershell
docker compose down
```

---

## Comparación

Después de tener los dos servicios funcionando hice algunas pruebas para comparar los resultados

### Tiempo de respuesta

Para REST obtuve

```text
0.011164 segundos
```

Que son aproximadamente

```text
11.164 ms
```

Para gRPC obtuve

```text
Tiempo total: 207.2118 ms
Conexión: 107.0432 ms
Llamada: 97.2345 ms
```

En mi prueba REST fue más rápido

---

## Bytes

También medí los bytes que se mandaron y recibieron durante una llamada

Los resultados fueron:

| Servicio | Recibidos | Enviados | Total |
|---|---:|---:|---:|
| REST | 620 bytes | 523 bytes | 1143 bytes |
| gRPC | 1100 bytes | 985 bytes | 2085 bytes |

En mi prueba REST usó menos bytes en total

En gRPC también apareció que la respuesta como tal tenía un tamaño aproximado de:

```text
9 bytes
```

Pero el total fue mayor porque también se cuenta la comunicación de la conexión

---

## Bytes de REST con curl

También hice otra prueba con `curl`

El resultado fue:

```text
Request: 150 bytes
Headers respuesta: 166 bytes
Body respuesta: 19 bytes
```

---

## Líneas del contrato

También comparé cuántas líneas tenía el contrato de cada servicio

Para REST usé:

```text
openapi.yaml
```

Para gRPC usé:

```text
calculator.proto
```

Los resultados fueron:

| Servicio | Archivo | Líneas |
|---|---|---:|
| REST | openapi.yaml | 34 |
| gRPC | calculator.proto | 12 |

El archivo de gRPC quedó mucho más corto

---

## Comparación final

| Característica | REST | gRPC |
|---|---|---|
| Operación | Sumar | Sumar |
| Resultado | 15 | 15 |
| Prueba | curl | grpcurl |
| Tiempo | 11.164 ms | 207.2118 ms |
| Bytes totales | 1143 bytes | 2085 bytes |
| Líneas del contrato | 34 | 12 |

---

## Conclusión

Con esta tarea pude hacer el mismo servicio usando REST y gRPC
REST se me hizo un poco más sencillo porque las pruebas se pueden hacer directamente con `curl` y usando JSON
gRPC tuvo unos pasos extra porque primero tuve que hacer el archivo `.proto` y después generar los archivos de Python.
En mis pruebas REST salió más rápido y usó menos bytes, mientras que el contrato de gRPC quedó mucho más corto
Pero la idea principal era comprobar que los dos servicios hacían exactamente lo mismo
Con esto pude ver de forma más práctica las diferencias entre REST y gRPC.