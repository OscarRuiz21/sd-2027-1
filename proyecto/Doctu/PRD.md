# PRD · DOCTU

Equipo: <DOCTU> · Integrantes: < @Jaramilloooo29 , @cortesangel , @Emiliano24246, @Tristanqsm > · Fecha: <09 de octubre de 2026>

## 1. Problema y usuarios
- Problema: En muchos consultorios y servicios de salud, la información del paciente se encuentra fragmentada entre registros, agendas o expedientes aislados. Esto puede generar dificultades para dar continuidad a la atención, menor claridad sobre las citas programadas y poco control sobre el acceso a información sensible. Los profesionales de la salud requieren herramientas digitales que organicen sus actividades sin depender de procesos manuales o información dispersa, mientras que los pacientes necesitan consultar su información con mayor claridad y seguridad.  
- Usuarios:  
  - Profesionales de la salud que necesitan organizar consultas, pacientes y expedientes. 
  - Consultorios y clínicas que buscan digitalizar parte de su operación.
  - Pacientes que requieren consultar información relacionada con su atención y mantener mayor control sobre su historial clínico.

## 2. Por qué es un sistema distribuido
El sistema integra distintos ecosistemas que requieren separación física y lógica por cuestiones de responsabilidad, seguridad e infraestructura.
Separación de capas: El frontend, backend y base de datos viven en entornos independientes que se comunican por red.

## 3. Objetivos y lo que no vamos a hacer
Objetivos:
Desarrollar una plataforma web distribuida que permita a pacientes y profesionales de la salud gestionar información de atención médica mediante interfaces diferenciadas, consultas de citas y expedientes clínicos de prueba, incorporando alertas de seguridad ante el acceso a información sensible. 
–Registro y autenticación diferenciada para pacientes y médicos.
–Gestión y consulta de citas médicas y expedientes clínicos centralizados.
–Sistema de trazabilidad con envío de alertas por correo electrónico ante el acceso a información clínica sensible.
–Despliegue local automatizado.

Lo que NO vamos a hacer:
Procesamiento de pagos reales: La pasarela de pagos es únicamente un prototipo visual; no procesará transacciones bancarias reales ni guardará tarjetas.
Datos reales de pacientes: Se usarán datos ficticios para la demostración y pruebas académicas.

## 4. El requisito que perseguimos y lo que dejamos atrás
Perseguimos: La interoperabilidad, centralización y la seguridad/trazabilidad de los datos sensibles (por eso se priorizó un servicio exclusivo de envío de alertas por correo cuando se abre un expediente). 
Dejamos atrás: La partición extrema de microservicios en la lógica de negocio. Para mantener la simplicidad y el tiempo de entrega.

## 5. Historias de usuario
- **Gestión de Agenda:** Como profesional de la salud, quiero visualizar mi agenda de citas diarias para organizar mi tiempo sin ausentismo. 
  - *Criterios de aceptación:* El sistema muestra las citas confirmadas del día. La consulta debe ser rápida y reflejar los estados de la cita.
- **Control de Expediente:** Como paciente, quiero acceder a mi historial clínico consolidado para tener el control sobre mis datos médicos.
  - *Criterios de aceptación:* Acceso mediante autenticación segura. Solo el paciente y el médico autorizado pueden ver los diagnósticos y notas.
- **Trazabilidad y Seguridad:** Como paciente, quiero recibir una notificación por correo electrónico cada vez que un médico consulte mis datos sensibles (CURP, contactos, expediente) para asegurar la transparencia.
  - *Criterios de aceptación:* El sistema de logs genera una traza auditable (Log_Accesos_Seguridad) y dispara un evento asíncrono para enviar el correo sin bloquear la consulta del médico.
- **Simulación de Pagos:** Como paciente, quiero ver una pasarela de pago al confirmar una cita para familiarizarme con el proceso de cobro.
  - *Criterios de aceptación:* La interfaz simula el cobro exitosamente, registrando la transacción en OLTP sin conectarse a un banco real.

## 6. Del monolito a los servicios
- **Módulos del monolito (conceptual):** Gestión de Usuarios, Agenda, Expedientes Clínicos, Logs/Trazabilidad, Pagos.
- **Servicios resultantes:** 
  1. **Servicio de Usuarios (PII y Perfiles):** Dueño de la tabla `Pacientes_PII` y `Profesionales_Perfiles`.
  2. **Servicio de Agenda y Pagos:** Dueño de `Agenda_Citas` y `Pagos_Transacciones`.
  3. **Servicio de Expediente Clínico:** Dueño de `Expediente_Consulta_Activa` y `Historial_Clinico_Consolidado`.
  4. **Servicio de Notificaciones (Alertas):** Recibe eventos de lectura de expedientes y envía correos.
- **Plan de ramas:** Uso de la metodología **Gitflow**. Rama `main` para código estable/entregables, `develop` para integración, y ramas `feature/nombre-funcionalidad` para desarrollo aislado.

## 7. Dónde vive cada pieza
| Pieza | Dónde vive | Por qué | Qué pasa si falla |
|---|---|---|---|
| **API gateway** | Contenedor Docker independiente | Expone un puerto único (ej. 8080) al frontend y enruta peticiones ocultando los puertos internos. | El cliente recibe un error 500/503 y pierde acceso a todos los servicios temporalmente. |
| **Directorio / DNS** | Contenedor Docker | Para que los servicios se anoten dinámicamente y no dependamos de IPs estáticas (hardcodeadas). | Los servicios dejan de registrarse, pero la caché temporal mantiene la comunicación brevemente hasta agotarse. |
| **Balanceo** | Integrado en el API Gateway | Para repartir la carga en caso de levantar múltiples réplicas de un servicio pesado. | Las peticiones podrían irse a una instancia caída temporalmente hasta que el directorio actualice el estado. |
| **Datos** | Máquina Virtual Oracle o contenedores PostgreSQL | Asegura el aislamiento de operaciones transaccionales rápidas vs. consultas analíticas pesadas. | El sistema completo no puede persistir ni leer datos (caída crítica). |
| **Operaciones entre servicios** | Llamadas HTTP/REST (o gRPC) en la red interna de Docker. | Permite que el Servicio de Expediente le avise al Servicio de Notificaciones de forma ligera. | Si la red interna falla, los servicios no pueden delegar tareas (ej. no se envían los correos de alerta). |
| **Manejo de fallas** | Configuración de Timeouts y reintentos en el cliente HTTP interno. | Para evitar bloqueos en cascada si el servicio de correo o agenda tarda en responder. | Fallos locales limitados, manteniendo el resto de la plataforma viva. |

## 8. Stack y cómo se levanta
- **Frontend:** React, Bootstrap, CSS puro.
- **Backend:** Servicios construidos en Java/Spring Boot o Node.js.
- **Base de Datos:** Oracle DB o contenedor de PostgreSQL estructurada con tablespaces `DOCTU_OPERACIONES` y `DOCTU_ANALITICA`.
- **Infraestructura:** Docker y Docker Compose.
- **Cómo se levanta:**
  1. Se levantara clonando el repositorio del proyecto
  2. Navegar al directorio y cambiar a la rama de trabajo.
  3. Ejecutar: `docker compose up --build -d` para levantar el Gateway, Directorio, Bases de Datos y los Microservicios.

## 9. Decisiones de arquitectura
- **Separación de bases de datos (OLTP vs OLAP):** Se descartó una sola tabla monolítica para todo. ¿Por qué? Para no trabar el sistema transaccional del consultorio cuando se soliciten reportes históricos y de trazabilidad pesados.
- **Despliegue contenerizado (Docker):** Se descartó instalar dependencias locales en cada máquina de los desarrolladores. ¿Por qué? Para estandarizar el entorno entre los 4 integrantes del equipo y evitar el "en mi máquina sí funciona".
- **Servicio aislado de Trazabilidad y Alertas:** Se descartó hacerlo síncrono en el mismo método de consulta. ¿Por qué? El envío de correos puede tardar; la consulta del expediente por el médico debe ser inmediata, delegando el correo a un proceso en segundo plano.
- **Patrón API Gateway:** Se descartó exponer todos los microservicios al frontend en distintos puertos. ¿Por qué? Por seguridad y simplicidad, el cliente de React solo apunta a una única URL.

## 10. Preguntas abiertas y desacuerdos
- ¿Qué proveedor de correos utilizaremos finalmente para garantizar que las notificaciones de trazabilidad no lleguen a SPAM?
- Aunque acordamos no particionar en extremo la lógica, ¿el servicio de Pagos (prototipo) debería vivir junto a Agenda o lo extraemos desde el principio previendo el futuro modelo Fintech?
- ¿Implementaremos comunicación gRPC entre los servicios internos para ganar velocidad en vez de llamadas REST tradicionales, o priorizamos la simplicidad de HTTP en el MVP?