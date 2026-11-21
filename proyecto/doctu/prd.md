# PRD — ACME: Packages Tracking

**Equipo:** ACME  
**Fecha:** 10 de octubre de 2026  
**Duración estimada:** 4 semanas

## Integrantes

- Arroyo Onofre Leonardo — @onofrela
- Guerrero López Uriel Ivan — @Ur182
- Meneses Calderas Grecia Irais — @grecia-i
- Muñoz San Agustin Victoria Monserrat — @brannvic

---

## 1. Problema y usuarios

### 1.1 Descripción del problema

Cuando una persona compra un producto por internet o envía un paquete, necesita conocer su ubicación y el estado de su entrega. Sin embargo, en sistemas con componentes demasiado dependientes entre sí, la caída de un servicio secundario, como el seguimiento o las notificaciones, puede afectar operaciones principales como el registro de un envío o la recepción de un paquete en almacén.

ACME busca resolver este problema mediante un sistema distribuido que permita registrar y conservar las operaciones principales incluso cuando algunos componentes no estén disponibles temporalmente.

El sistema utilizará comunicación asíncrona para que los cambios de estado se propaguen cuando los servicios estén disponibles, sin exigir que todos respondan al mismo tiempo.

### 1.2 Usuarios principales

- **Cliente final:** consulta el estado y el historial de su paquete.
- **Operador de Envíos:** registra paquetes y genera guías de rastreo.
- **Operador de Almacén:** registra la recepción física y los movimientos de paquetes.
- **Sistema de Notificaciones:** informa al cliente sobre los cambios de estado mediante correos electrónicos de prueba.

### 1.3 Objetivo principal

Garantizar que las operaciones confirmadas se conserven ante fallos temporales de RabbitMQ y de los servicios secundarios, permitiendo que los eventos pendientes se procesen cuando se restablezca la comunicación.

**Principio de diseño:** es preferible que el seguimiento tarde unos segundos en actualizarse a rechazar una operación válida porque un servicio secundario no está disponible.

Esta garantía depende de que la transacción local se confirme correctamente y de que la persistencia esté configurada de forma adecuada. No significa que el sistema sea inmune a cualquier pérdida de infraestructura o de datos.

---

## 2. ¿Por qué es un sistema distribuido?

ACME se organizará en cuatro microservicios independientes, comunicados mediante una API REST y eventos asíncronos.

La separación permite aislar responsabilidades y reducir la dependencia directa entre las operaciones principales y los servicios secundarios.

### 2.1 Beneficios esperados

- Registrar envíos aunque RabbitMQ esté temporalmente fuera de servicio.
- Registrar movimientos físicos en Almacén sin depender de Tracking o Notificaciones.
- Recuperar eventos pendientes después de una interrupción.
- Mantener independientes los datos y responsabilidades de cada servicio.
- Permitir que Tracking y Notificaciones procesen los cambios de manera asíncrona.
- Demostrar tolerancia a fallos mediante pruebas reproducibles.

### 2.2 Costos y compromisos

Una arquitectura distribuida introduce latencia de red, posibles fallos de comunicación, consistencia eventual, duplicación de mensajes y mayor complejidad de diagnóstico.

ACME acepta estos costos porque su prioridad es la durabilidad de las operaciones confirmadas y la disponibilidad parcial, no lograr consistencia inmediata en todos los servicios.

---

## 3. Alcance del proyecto

### 3.1 Funcionalidades incluidas

- Registro de paquetes y generación de un identificador de rastreo.
- Consulta del estado y del historial de checkpoints.
- Registro de movimientos físicos en almacén.
- Actualización del estado oficial del paquete.
- Publicación y consumo asíncrono de eventos mediante RabbitMQ.
- Persistencia de eventos pendientes mediante Transactional Outbox.
- Procesamiento idempotente de eventos.
- Envío de correos a un servidor de pruebas local.
- Manejo de errores HTTP, timeouts y Circuit Breaker.
- Pruebas de resiliencia mediante Docker Compose y Postman.

### 3.2 Exclusiones explícitas

Para completar el proyecto en el plazo establecido, quedan fuera del alcance:

- Integraciones con paqueterías reales, como DHL o FedEx.
- Pasarelas de pago y cobros con tarjeta.
- Envío de SMS reales.
- Envío de correos reales a clientes.
- Desarrollo de una interfaz web completa.
- Autenticación avanzada mediante OAuth2 o JWT.
- Módulos de devoluciones, soporte, facturación y gestión de usuarios.
- Replicación, sharding y escalamiento masivo de bases de datos.

Las solicitudes y respuestas se validarán principalmente mediante Postman. Se utilizará MailHog o Mailpit para capturar correos de prueba en un entorno local.

---

## 4. Prioridad arquitectónica y decisiones principales

La prioridad del equipo es **mantener la durabilidad de las operaciones confirmadas y permitir la disponibilidad parcial del sistema ante fallos**.

### 4.1 Principio de durabilidad

Cuando Envíos confirma un nuevo paquete, debe haber guardado tanto el registro del paquete como el evento pendiente en una misma transacción local de PostgreSQL.

Si RabbitMQ está caído, la operación principal puede confirmarse y el evento permanece pendiente en Outbox.

### 4.2 Consistencia eventual

Los cambios no necesariamente aparecerán al mismo tiempo en todos los servicios. Tracking puede mostrar temporalmente el último checkpoint procesado, mientras que Notificaciones puede enviar un correo después de la confirmación de la operación original.

### 4.3 Comunicación asíncrona

Los servicios intercambiarán eventos mediante RabbitMQ, evitando llamadas HTTP síncronas innecesarias entre servicios secundarios.

### 4.4 Idempotencia

Cada evento tendrá un identificador único (`event_id`). Los consumidores registrarán los eventos procesados para evitar duplicar las modificaciones locales cuando RabbitMQ entregue nuevamente un mensaje.

La idempotencia local no garantiza que un proveedor externo de correo envíe exactamente un correo en todos los escenarios de fallo.

---

## 5. Arquitectura y responsabilidades

ACME tendrá cuatro microservicios de negocio, un API Gateway y la infraestructura de mensajería.

| Componente | Tecnología | Responsabilidad |
|---|---|---|
| API Gateway | Spring Cloud Gateway | Entrada de peticiones, enrutamiento y respuestas de error controladas. |
| Envíos (`shipments-service`) | Java 17, Spring Boot 3 y PostgreSQL | Registro de paquetes y autoridad sobre su estado oficial. |
| Almacén (`warehouse-service`) | Java 17, Spring Boot 3 y PostgreSQL | Registro de recepciones y movimientos físicos. |
| Tracking (`tracking-service`) | Java 17, Spring Boot 3 y MongoDB | Consulta e historial de checkpoints procesados. |
| Notificaciones (`notifications-service`) | Java 17, Spring Boot 3 y PostgreSQL | Procesamiento de eventos, historial y envío de correos de prueba. |
| Broker de mensajes | RabbitMQ | Encolamiento, publicación y distribución de eventos. |
| Servidor de correo de prueba | MailHog o Mailpit | Captura y consulta de los correos generados. |
| Infraestructura | Docker Compose | Ejecución de contenedores, redes internas y volúmenes persistentes. |

### 5.1 Fuente de verdad del estado oficial

`shipments-service` será la única autoridad para modificar el estado oficial del paquete.

Las responsabilidades se delimitan de la siguiente manera:

- **Almacén:** registra el movimiento físico, por ejemplo, «Paquete recibido en Almacén Norte», y emite un evento.
- **Envíos:** consume el evento, valida las reglas de negocio y actualiza el estado oficial, por ejemplo, de `CREADO` a `EN_ALMACEN`.
- **Tracking:** refleja los cambios procesados en el historial de checkpoints.
- **Notificaciones:** consume los eventos correspondientes y genera los correos de aviso.

Esta separación evita que distintos servicios modifiquen independientemente el estado oficial y produzcan contradicciones.

### 5.2 Persistencia independiente

Cada servicio tendrá propiedad exclusiva sobre sus datos:

- Envíos: PostgreSQL.
- Almacén: PostgreSQL.
- Tracking: MongoDB.
- Notificaciones: PostgreSQL.

No se realizarán consultas directas a las tablas internas de otro microservicio. Las bases de datos utilizarán volúmenes persistentes de Docker; estos volúmenes no sustituyen las copias de seguridad.

### 5.3 Flujo general de eventos

1. Un operador registra un paquete o un movimiento físico.
2. El servicio responsable persiste la operación y el evento Outbox en una transacción local.
3. Un proceso de fondo publica el evento en RabbitMQ.
4. RabbitMQ distribuye el evento a los consumidores correspondientes.
5. Envíos actualiza el estado oficial cuando recibe eventos de movimientos físicos.
6. Tracking actualiza su historial y Notificaciones procesa los avisos.
7. Los consumidores confirman los mensajes después de completar y persistir su procesamiento local.

---

## 6. Historias de usuario y criterios de aceptación

### HU-01. Consulta del estado de un paquete

**Como** cliente final de ACME,  
**quiero** consultar el estado de mi paquete mediante su identificador único (`tracking_id`),  
**para** conocer el último checkpoint procesado por el sistema.

**Endpoint:** `GET /api/v1/tracking/{trackingId}`

**Criterios de aceptación:**

1. **Consulta exitosa:** si la guía existe en Tracking, responde `200 OK` con el estado, la ubicación y el historial de checkpoints procesados.
2. **Guía no encontrada:** si la guía no existe en MongoDB, responde `404 Not Found` con un mensaje informativo.
3. **Consistencia eventual:** si hay un evento pendiente, devuelve el último estado disponible en Tracking. Cuando el evento se procese, las siguientes consultas reflejarán la actualización.
4. **Servicio no disponible:** si Tracking o MongoDB no responden, el Gateway devuelve `503 Service Unavailable` cuando se agota la espera o el Circuit Breaker impide la llamada.

Ejemplo de respuesta para una guía no encontrada:

```json
{
  "status": "NOT_FOUND",
  "message": "La guía ingresada no fue encontrada o se encuentra en proceso de sincronización inicial. Verifique el código o reintente en un momento.",
  "tracking_id": "ACME-99999"
}
```

El `404` no permite distinguir con certeza una guía inválida de una guía recién creada cuyo evento todavía no ha llegado a Tracking.

### HU-02. Registro de un envío

**Como** operador de Envíos,  
**quiero** registrar un paquete con los datos del remitente, destinatario y dirección,  
**para** generar una guía de rastreo y conservar la operación aunque RabbitMQ esté fuera de servicio.

**Endpoint:** `POST /api/v1/shipments`

**Criterios de aceptación:**

1. El servicio guarda el paquete y el evento Outbox en una única transacción local de PostgreSQL.
2. Cuando la transacción se confirma, responde `201 Created` y devuelve el `tracking_id`.
3. Si RabbitMQ está caído, el registro sigue siendo exitoso y el evento permanece en estado `PENDING`.
4. Cuando RabbitMQ se recupera, el publicador envía los eventos pendientes.
5. El evento se marca como `PROCESSED` después de recibir el Publisher Confirm del broker.

`PROCESSED` significa que RabbitMQ confirmó la publicación, no que todos los consumidores terminaron de procesar el evento.

### HU-03. Recepción física en Almacén

**Como** operador de Almacén,  
**quiero** registrar la recepción física de un paquete mediante su `tracking_id`,  
**para** conservar el movimiento y comunicarlo al resto del sistema sin depender de servicios secundarios.

**Endpoint:** `POST /api/v1/warehouse/checkin`

**Criterios de aceptación:**

1. Almacén registra el movimiento y su evento Outbox en una misma transacción local de PostgreSQL.
2. Una vez confirmada la transacción, responde `201 Created` o `200 OK`, según el contrato definitivo del endpoint.
3. La caída de Tracking o Notificaciones no impide confirmar el movimiento local.
4. El evento se publica cuando RabbitMQ está disponible.
5. Envíos consume el evento, valida las reglas de negocio y actualiza el estado oficial.
6. Tracking y Notificaciones procesan posteriormente los eventos correspondientes.

### HU-04. Notificaciones asíncronas

**Como** cliente final de ACME,  
**quiero** recibir un correo electrónico cuando cambie el estado de mi paquete,  
**para** conocer el avance de mi envío sin consultar constantemente la aplicación.

**Criterios de aceptación:**

1. Notificaciones consume los eventos de cambio de estado desde RabbitMQ.
2. Selecciona la plantilla correspondiente y envía el correo al servidor de pruebas MailHog o Mailpit.
3. Registra localmente el procesamiento y utiliza `event_id` para evitar duplicados en su base de datos.
4. Si Notificaciones está detenido, los mensajes permanecen en una cola durable y se procesan al recuperar el consumidor.
5. Si el proveedor de correo falla, se aplican reintentos con backoff exponencial.
6. Después de tres reintentos fallidos, el mensaje se desvía a una Dead-Letter Queue (DLQ), conforme a la configuración que se implemente.

La DLQ permitirá inspeccionar y recuperar mensajes fallidos. No implica que los correos se reenvíen automáticamente sin una política de recuperación.

---

## 7. Patrones de resiliencia y manejo de errores

### 7.1 Transactional Outbox

En Envíos y Almacén, la operación principal y su evento se guardarán en la misma transacción local.

Esto evita confirmar una operación sin dejar registrado el evento que debe propagarse.

Cuando RabbitMQ esté disponible, un proceso de fondo publicará los eventos pendientes con Publisher Confirms y actualizará su estado.

### 7.2 Entrega y consumo de mensajes

- Se utilizarán colas durables y mensajes persistentes.
- Cada evento tendrá un `event_id` único.
- Los consumidores mantendrán un registro de eventos procesados.
- La actualización local y el registro del `event_id` se realizarán dentro de una misma transacción.
- El Consumer ACK se enviará después de confirmar la persistencia local.
- Los fallos permanentes se gestionarán mediante reintentos y DLQ.

La estrategia de mensajería será de entrega *at-least-once*. Pueden existir reentregas y, en determinados fallos externos, duplicados de correo.

### 7.3 API Gateway y Circuit Breaker

Configuración inicial propuesta:

- Timeout por intento: 2 segundos.
- Un reintento después de un fallo de red o timeout.
- Pausa entre reintentos: 500 milisegundos.
- Apertura del Circuit Breaker cuando los fallos superen el 50 % de una ventana de 10 solicitudes.
- Espera de 10 segundos antes de probar la recuperación.
- Respuesta `503 Service Unavailable` cuando el circuito esté abierto o el servicio no pueda atender la petición.

Se debe validar el tiempo máximo total de cada solicitud, ya que los reintentos pueden superar los dos segundos de timeout individual.

### 7.4 Descubrimiento y balanceo

Docker Compose proporcionará DNS interno para resolver los nombres de los servicios.

El DNS no garantiza por sí solo que las solicitudes alternen entre instancias ni que se excluyan automáticamente las instancias fallidas. El balanceo y las comprobaciones de salud deberán configurarse explícitamente si se utilizan múltiples instancias.

---

## 8. Guion de pruebas de resiliencia para la demostración final

Las pruebas siguientes son criterios de aceptación. Se considerarán aprobadas cuando se ejecuten y se adjunten evidencias.

### Prueba 1. Caída de RabbitMQ y recuperación de Outbox

**Objetivo:** demostrar que un envío puede confirmarse aunque el broker de mensajes esté temporalmente fuera de servicio.

**Paso 1. Línea base**

- Verificar con `docker compose ps` que los servicios y las bases de datos estén activos.
- Registrar el paquete `ACME-DEMO-01` mediante `POST /api/v1/shipments`.
- Comprobar el seguimiento y la recepción del correo en MailHog o Mailpit.

**Resultado esperado:** registro exitoso y flujo normal de eventos.

**Paso 2. Inyección de fallo**

Ejecutar:

```bash
docker compose stop rabbitmq
```

**Paso 3. Registro bajo fallo**

Registrar `ACME-DEMO-02` mediante `POST /api/v1/shipments`.

**Resultado esperado:**

- Respuesta `201 Created`.
- Paquete guardado en PostgreSQL.
- Evento guardado en Outbox con estado `PENDING`.
- Ausencia de un error provocado únicamente por la caída de RabbitMQ.

**Paso 4. Verificación de degradación**

- Comprobar que `ACME-DEMO-02` todavía no existe en MongoDB.
- Consultar `GET /api/v1/tracking/ACME-DEMO-02`.

**Resultado esperado:** `404 Not Found` si el evento inicial aún no ha sido procesado. Si Tracking ya recibió el evento, la respuesta correcta será `200 OK`.

**Paso 5. Recuperación**

Ejecutar:

```bash
docker compose start rabbitmq
```

Comprobar por separado:

1. El evento pendiente se publica y Outbox pasa a `PROCESSED`.
2. Tracking procesa el evento y devuelve `200 OK` en la siguiente consulta.
3. Notificaciones procesa el evento y el correo aparece en MailHog o Mailpit.

### Prueba 2. Caída de Tracking y Notificaciones

**Objetivo:** demostrar que el registro de un movimiento físico no depende de la disponibilidad de los servicios secundarios.

**Paso 1. Inyección de fallo**

Ejecutar:

```bash
docker compose stop tracking-service notifications-service
```

**Paso 2. Operación en Almacén**

Registrar la recepción física de `ACME-DEMO-01` mediante `POST /api/v1/warehouse/checkin`.

**Resultado esperado:**

- Respuesta exitosa al operador.
- Movimiento guardado en PostgreSQL de Almacén.
- Evento registrado en su Outbox.
- Ninguna llamada síncrona obligatoria a Tracking o Notificaciones.

**Paso 3. Recuperación**

Ejecutar:

```bash
docker compose start tracking-service notifications-service
```

**Resultado esperado:**

1. RabbitMQ y los consumidores procesan los eventos pendientes.
2. Envíos actualiza el estado oficial si corresponde.
3. Tracking incorpora el checkpoint de almacén.
4. Notificaciones procesa el evento y el correo aparece en MailHog o Mailpit.

### Evidencias obligatorias

El equipo guardará:

- Logs de Docker.
- Solicitudes y respuestas de Postman.
- Consultas de verificación de PostgreSQL y MongoDB.
- Estados de los eventos Outbox.
- Evidencia de los mensajes procesados y de los mensajes fallidos.
- Capturas del correo recibido en MailHog o Mailpit.

---

## 9. Plan de trabajo de cuatro semanas

### Semana 1. Infraestructura y contratos

**Objetivo:** tener un entorno reproducible.

- Crear la estructura de los cuatro microservicios.
- Configurar Java 17, Spring Boot 3 y Docker Compose.
- Levantar PostgreSQL, MongoDB, RabbitMQ y MailHog o Mailpit.
- Definir los contratos REST y los esquemas de eventos.
- Validar la comunicación entre contenedores.

**Entregable:** infraestructura levantada y contratos documentados.

### Semana 2. Flujo de Envíos y Tracking

**Objetivo:** implementar el primer flujo funcional.

- Crear el endpoint de registro de paquetes.
- Implementar Transactional Outbox en Envíos.
- Publicar y consumir el evento de creación.
- Persistir el historial en MongoDB.
- Implementar la consulta de seguimiento.

**Entregable:** registro y consulta funcionales con consistencia eventual.

### Semana 3. Almacén, Notificaciones y Gateway

**Objetivo:** integrar los servicios restantes.

- Implementar la recepción física en Almacén y su Outbox.
- Hacer que Envíos procese los eventos de Almacén.
- Implementar Notificaciones, deduplicación y reintentos.
- Integrar MailHog o Mailpit.
- Configurar API Gateway, timeouts y Circuit Breaker.
- Validar los contratos con Postman.

**Entregable:** flujo completo de eventos entre los cuatro servicios.

### Semana 4. Resiliencia y presentación

**Objetivo:** demostrar el comportamiento bajo fallos.

- Ejecutar las dos pruebas de resiliencia.
- Corregir problemas de persistencia, reintentos y consumo de mensajes.
- Recopilar logs y capturas de evidencia.
- Preparar la demostración y explicar las decisiones de arquitectura.
- Revisar el proyecto contra los criterios académicos.

**Entregable:** demostración reproducible, evidencias y presentación final.

### Distribución inicial del trabajo

- **Leonardo y Uriel:** Gateway, Envíos, Almacén y persistencia PostgreSQL.
- **Grecia y Victoria:** Tracking, Notificaciones, MongoDB, RabbitMQ y MailHog/Mailpit.

La distribución es inicial; los contratos y las pruebas de integración serán responsabilidad compartida de los cuatro integrantes.

---

## 10. Preguntas abiertas y decisiones técnicas por validar

No existen desacuerdos pendientes sobre el alcance ni sobre la autoridad del estado oficial. Las siguientes decisiones deben concretarse durante la implementación:

1. Definir los esquemas exactos de los eventos y los campos obligatorios de cada petición REST.
2. Definir cómo se gestionan eventos duplicados o fuera de orden.
3. Establecer el mecanismo para reclamar eventos Outbox pendientes sin que dos publicadores los procesen simultáneamente.
4. Concretar los tiempos de backoff y la configuración de la DLQ.
5. Validar el timeout total del Gateway, incluyendo los reintentos.
6. Determinar cómo se comportará el sistema si Envíos recibe un evento de Almacén para una guía inexistente.
7. Configurar y probar la persistencia de mensajes en RabbitMQ.
8. Documentar la autenticación simulada utilizada en el laboratorio.
9. Ejecutar las pruebas de resiliencia y adjuntar sus evidencias.

---

## Criterios finales de éxito

El proyecto ACME se considerará funcional cuando el equipo demuestre que:

1. Un paquete puede registrarse y responder `201 Created` aunque RabbitMQ esté caído, siempre que PostgreSQL confirme la transacción local.
2. Un movimiento de almacén puede persistirse mientras Tracking y Notificaciones están detenidos.
3. Los eventos pendientes se publican y procesan tras la recuperación de RabbitMQ y de los consumidores.
4. Tracking devuelve `200 OK`, `404 Not Found` o `503 Service Unavailable` según la condición real del servicio.
5. Los consumidores controlan duplicados y fallos de entrega mediante idempotencia, reintentos y DLQ.
6. Las dos pruebas de resiliencia pueden repetirse y producir evidencias verificables.
7. El equipo puede explicar los beneficios, costos y límites de su arquitectura distribuida.

**Resultado esperado:** una demostración académica funcional que evidencie la durabilidad de las operaciones confirmadas, la disponibilidad parcial y la recuperación mediante comunicación asíncrona.
