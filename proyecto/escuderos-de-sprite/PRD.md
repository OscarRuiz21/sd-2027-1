# PRD · Plataforma Logística de Envíos

Equipo: Escuderos de Sprite

Integrantes:
Martínez Trejo Jesus - @jesusmtz20
Perez Nava Francisco Javier - @FranciscoNava1702
Santiago González Kevin - @KevinSantiag0
Victoria Domíngez Alejandro - @VicAlDo23

Fecha: 9 de octubre de 2026

## 1. Problema y usuarios

Una plataforma logística para simplificar la gestión y el rastreo de envíos para usuarios sin experiencia. Coordina el flujo de entrega entre clientes (remitentes y destinatarios), operadores de sucursal (recepción, salida y asignación) y repartidores (actualización en ruta).

## 2. Por qué es un sistema distribuido

Para evitar un punto único de falla (alta disponibilidad), soportar la alta carga transaccional y el volumen de datos generados, y mejorar la accesibilidad y rendimiento a nivel nacional.

## 3. Objetivos y lo que no vamos a hacer

**Objetivos:**

- Generar y registrar solicitudes de envío.
- Procesar cobros por los servicios de entrega.
- Rastrear los paquetes de forma constante.
- Gestionar detalles operativos y confirmar entregas.

**No vamos a hacer:**

- Gestión de nómina de repartidores.
- Algoritmos de optimización de rutas.
- Desarrollo del frontend de la aplicación.

## 4. El requisito que perseguimos y lo que dejamos atrás

Se prioriza la **alta disponibilidad** y se acepta una **consistencia eventual** para el rastreo de paquetes (el estado puede tardar unos minutos en actualizarse para el usuario). Sin embargo, se exige **consistencia fuerte** y exacta para el procesamiento de pagos.

## 5. Historias de usuario

**Generación y pago de envío**

- _Historia:_ Como remitente sin experiencia en envíos, quiero registrar y pagar mi solicitud de envío capturando los datos de origen, destino y paquete, para obtener un número de guía con el que pueda dar seguimiento a mi paquete sin tener que acudir a una sucursal.
- _Criterios de aceptación:_
  - **Envío exitoso:** Dado un remitente con datos completos y un pago aprobado, cuando envía la solicitud, entonces el sistema genera un número de guía único, el envío queda en estado CONFIRMADO y el remitente recibe una notificación con su guía.
  - **Pago rechazado:** Dado un pago rechazado por Cobros, cuando el remitente envía la solicitud, entonces no se genera número de guía, el envío queda como CANCELADO y se notifica al cliente el motivo.
  - **Falla a la mitad del proceso:** Dado que el pago fue preautorizado, cuando Gestión de detalles no logra guardar el envío después de los reintentos, entonces la preautorización se anula, no se realiza ningún cargo al cliente y el envío queda como CANCELADO.

## 6. Del monolito a los servicios

**Módulos del monolito:** Registro de envíos, Consulta de envíos, Cobros, Notificaciones, Gestión de detalles.

**Servicios resultantes y dueño de datos:**

1. **Cobros:** Dueño de la información financiera de los clientes y costos del negocio.
2. **Notificaciones:** Dueño de los medios de envío y asociación de eventos a notificaciones.
3. **Monitoreo:** Dueño del rastreo, rutas completas, estados y lo que se muestra al usuario.
4. **Registro:** Dueño de la información de envíos y datos personales de remitentes/destinatarios.
5. **Gestión de detalles:** Dueño de los roles, acciones de empleados y eventos emergentes del trayecto.

**Llamadas en red (flujo de registro):**
`Registro` se comunica con `Cobros`. Tras la transacción, `Registro` llama a `Notificaciones`. Si el pago es exitoso, `Registro` llama a `Gestión de detalles`. A partir de ahí, cada evento en `Gestión de detalles` se comunica a `Notificaciones` y `Monitoreo`.

## 7. Dónde vive cada pieza

| Pieza                           | Dónde vive                                                                           | Por qué                                                                                                         | Qué pasa si falla                                                                                                        |
| ------------------------------- | ------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| **API gateway**                 | Múltiples instancias detrás de un balanceador de carga.                              | Ser la puerta única para clientes, operadores y repartidores sin ser un punto único de falla.                   | El balanceador redirige automáticamente las solicitudes a otra instancia sana.                                           |
| **Directorio / DNS**            | Service Discovery.                                                                   | Permitir que los servicios se encuentren dinámicamente en la red.                                               | Se utiliza una cola temporal para retener un número limitado de peticiones; si se supera el límite, se rechazan.         |
| **Balanceo**                    | Nginx distribuyendo tráfico a réplicas.                                              | Repartir la carga en los servicios más pesados (Cobros, Monitoreo, Gestión de detalles).                        | El sistema detecta la falla y levanta una nueva instancia de Nginx en segundos.                                          |
| **Datos**                       | PostgreSQL (Relacional) en los 5 servicios, con fragmentación horizontal geográfica. | Mantiene consistencia de forma sencilla. El particionamiento geográfico alivia la carga separando por regiones. | Si una partición cae, solo afecta a los usuarios de esa región; los demás siguen operando.                               |
| **Operaciones entre servicios** | Orquestado desde `Registro` (patrón de preautorización / retención).                 | Permite asegurar fondos sin cobrarlos hasta que la información del paquete se guarde.                           | Si `Gestión de detalles` falla tras reintentar, `Registro` pide a `Cobros` anular retención, marca CANCELADO y notifica. |
| **Manejo de fallas**            | Llaves de idempotencia y Timeouts.                                                   | Evitan bloqueos por espera indefinida y duplicidad de transacciones.                                            | Las peticiones fallan rápido y los reintentos automáticos no cobran ni registran dos veces el mismo paquete.             |

## 8. Stack y cómo se levanta

- **Lenguaje y Framework:** Python con Django.
- **Infraestructura:** Contenedores con Docker (y Docker Compose).
- **Razón:** Experiencia previa del equipo con estas herramientas, facilidad de implementación rápida y buena capacidad de escalabilidad.

## 9. Decisiones de arquitectura

- **Decisión:** Base de datos relacional (PostgreSQL) con fragmentación horizontal.
  - **Alternativa descartada:** Base de datos NoSQL.
  - **Por qué:** El modelo relacional permite mantener la consistencia de la información de manera más nativa y sencilla para este dominio de negocio, y la escala se maneja fragmentando geográficamente.
- **Decisión:** Consistencia eventual en monitoreo, fuerte en cobros.
  - **Alternativa descartada:** Consistencia fuerte en todo el sistema (transacciones distribuidas 2PC).
  - **Por qué:** Garantiza la alta disponibilidad del sistema para los usuarios de rastreo sin sacrificar la exactitud del dinero de la empresa.

## 10. Preguntas abiertas y desacuerdos

- El frontend está fuera de alcance, por lo que el equipo deberá definir más adelante quién o qué herramienta consumirá el API Gateway para realizar las pruebas integrales de las historias de usuario.
