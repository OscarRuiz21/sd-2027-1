# Corrección · Lab S07: Mexi Banco (Directorio, Gateway y Balanceo)

**Alumno:** Sánchez Mayén Tristán Qesen  
**Cuenta:** 320187072  
**Corte:** 4-nov-2026  

---

## Observación de la revisión
> **Qué hay que corregir:**  
> S07: los dos 404 del gateway no dicen quién produjo cada uno.  
>  
> **Cómo se corrige:**  
> Muestra el GET a `/api/cuentas/000` (lo contesta cuenta, trae X-Instancia) y el POST a `/api/movimientos` (lo contesta el gateway, sin X-Instancia) y explica la diferencia.

---

## 1. Petición: `GET http://localhost:8080/api/cuentas/000`

### Evidencia de ejecución
```bash
$ curl -i http://localhost:8080/api/cuentas/000
```

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json
Date: Sat, 10 Oct 2026 19:42:15 GMT
X-Instancia: f638bf97b1db
transfer-encoding: chunked

{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "No existe la CLABE 000",
  "instance": "/cuentas/000"
}
```

### ¿Quién lo produjo?
**El microservicio `cuenta`**.

### Explicación
1. El cliente envía la petición `GET /api/cuentas/000` al puerto `8080` del **API Gateway**.
2. El Gateway consulta su configuración de rutas en `gateway/src/main/resources/application.yml` y encuentra una regla coincidente:
   ```yaml
   - id: cuenta
     uri: lb://CUENTA
     predicates:
       - Path=/api/cuentas/**
     filters:
       - StripPrefix=1
   ```
3. El Gateway le pregunta al directorio **Eureka** por las instancias vivas registradas bajo el nombre `CUENTA`.
4. El balanceador de carga selecciona una réplica saludable y el filtro `StripPrefix=1` reescribe la ruta a `/cuentas/000`.
5. La llamada viaja por la red interna Docker hasta el contenedor del microservicio `cuenta` (en este caso con ID `f638bf97b1db`).
6. El microservicio `cuenta` ejecuta la consulta en su base de datos PostgreSQL local y determina que la cuenta con CLABE `000` no existe.
7. `cuenta` emite un error `404 Not Found` en formato JSON RFC 7807 (`"detail": "No existe la CLABE 000"`).
8. La respuesta pasa de vuelta a través del Gateway hacia el cliente, conservando la cabecera **`X-Instancia: f638bf97b1db`**. Esta cabecera demuestra de manera irrefutable que **la petición cruzó el Gateway y fue atendida por el microservicio backend `cuenta`**.

---

## 2. Petición: `POST http://localhost:8080/api/movimientos`

### Evidencia de ejecución
```bash
$ curl -i -X POST http://localhost:8080/api/movimientos
```

```http
HTTP/1.1 404 Not Found
content-length: 0
date: Sat, 10 Oct 2026 19:42:25 GMT
```

### ¿Quién lo produjo?
**El propio API Gateway**.

### Explicación
1. El cliente envía una petición `POST /api/movimientos` al puerto `8080` del Gateway intentando registrar un movimiento contable desde el exterior.
2. En `application.yml`, la ruta hacia el microservicio `movimiento` está definida con un predicado estricto que únicamente admite el método HTTP `GET`:
   ```yaml
   - id: movimiento
     uri: lb://MOVIMIENTO
     predicates:
       - Path=/api/movimientos/**
       - Method=GET
     filters:
       - StripPrefix=1
   ```
3. Registrar movimientos (`POST`) es una operación de negocio crítica y sensible que **no debe estar expuesta hacia clientes externos**. Únicamente los microservicios internos del banco (como `transferencia` o `spei`) tienen permitido registrar movimientos conectándose directamente mediante la red interna de Docker.
4. Cuando el Gateway procesa el `POST /api/movimientos`, evalúa los predicados y encuentra que el método HTTP no coincide con `GET`.
5. Como ninguna otra ruta coincide, el Gateway **frena la petición de inmediato en el perímetro (la puerta de entrada)** y genera directamente un error HTTP `404 Not Found`.
6. La petición **jamás sale hacia la red interna, jamás consulta a Eureka y el microservicio `movimiento` nunca se entera de la llamada**.
7. Debido a que ningún contenedor de backend intervino en la respuesta, **NO existe cabecera `X-Instancia`**.

---

## 3. Comparativa y diferencia fundamental

| Característica | `GET /api/cuentas/000` | `POST /api/movimientos` |
|---|---|---|
| **Código HTTP** | `404 Not Found` | `404 Not Found` |
| **Cabecera `X-Instancia`** | **Presente** (e.g. `X-Instancia: f638bf97b1db`) | **Ausente** (sin cabecera `X-Instancia`) |
| **Cuerpo de respuesta** | JSON de dominio (`"No existe la CLABE 000"`) | Vacío (`content-length: 0`) o genérico de Gateway |
| **Quién generó el 404** | **Microservicio `cuenta`** | **API Gateway** |
| **Tipo de error** | **404 de Negocio / Datos:** La ruta existe, la petición llegó al servicio, pero el recurso específico no existe en la base de datos. | **404 de Infraestructura / Seguridad Perimetral:** El Gateway actuó como firewall de aplicación bloqueando un método no expuesto al público. |
