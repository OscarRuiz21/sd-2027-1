# PRD — Sistema Integral de Gestión de Recursos Humanos (SIGRH)

**Proyecto final — Sistemas Distribuidos**  
**Institución:** Facultad de Ingeniería, Universidad Nacional Autónoma de México  
**Equipo:** Los Monolitos
**Estado:** Propuesta de requisitos y arquitectura inicial  
---

## 1. Problema y usuarios

### 1.1. Descripción del problema

Las organizaciones necesitan administrar diferentes procesos relacionados con sus recursos humanos, como el reclutamiento, la contratación, el registro de empleados, las vacaciones y la nómina. Aunque estas actividades tienen responsabilidades diferentes, requieren intercambiar información para mantener la continuidad de los procesos administrativos.

En algunas pequeñas y medianas empresas (PyMEs), esta información todavía se administra mediante documentos físicos, hojas de cálculo o herramientas independientes. Esto puede provocar registros duplicados, actualizaciones tardías, dificultades de coordinación y errores al compartir información entre departamentos.

Por ejemplo, cuando una persona es contratada, sus datos deben incorporarse a Administración de Personal y posteriormente estar disponibles para Nómina y Vacaciones. Del mismo modo, una solicitud de vacaciones aprobada debe reflejarse en los registros correspondientes y comunicarse a Nómina cuando sea necesario.

La falta de coordinación puede generar trabajo administrativo adicional y afectar la confiabilidad de la información utilizada en los cálculos de nómina.

### 1.2. Solución propuesta

Se propone desarrollar el **Sistema Integral de Gestión de Recursos Humanos (SIGRH)**, una plataforma web que integre los principales procesos de Recursos Humanos mediante una arquitectura de servicios distribuidos.

El sistema permitirá administrar candidatos, empleados, vacaciones y nómina, manteniendo responsabilidades separadas y estableciendo mecanismos de comunicación para compartir los cambios relevantes.

La solución buscará conservar la disponibilidad de los procesos independientes cuando uno de los servicios presente fallas, así como garantizar que los cálculos definitivos de nómina se realicen únicamente con información completa y validada.

### 1.3. Usuarios

El sistema estará dirigido inicialmente a pequeñas y medianas empresas, sin descartar su adaptación futura a organizaciones de mayor tamaño.

Se contemplan tres tipos principales de usuarios:

**Administrador general:** responsable de la configuración del sistema, la gestión de usuarios, la asignación de permisos y la supervisión de operaciones.

**Personal de Recursos Humanos:** responsable de registrar candidatos, administrar expedientes laborales, evaluar solicitudes de vacaciones y realizar las operaciones autorizadas de nómina.

**Empleado:** usuario con acceso limitado para consultar y descargar sus propios recibos de nómina, consultar sus días disponibles y registrar solicitudes de vacaciones.

Cada usuario contará con permisos asociados a su función, evitando el acceso no autorizado a información personal o salarial.

## 2. Por qué es un sistema distribuido

SIGRH se propone como un sistema distribuido porque sus principales procesos tienen cargas de trabajo, responsabilidades y necesidades de mantenimiento diferentes.

Por ejemplo, el servicio de Nómina puede necesitar procesar numerosos cálculos relacionados con salarios, bonos y deducciones. En una organización con muchos empleados, estas operaciones pueden requerir más recursos que las actividades de Reclutamiento.

Una arquitectura distribuida permitirá aumentar los recursos de Nómina sin necesidad de incrementar los de los demás servicios.

También permitirá realizar cambios en un módulo sin modificar directamente la implementación de los otros. Esto resulta útil cuando cambian las reglas de cálculo de nómina o los procedimientos administrativos.

Otra razón importante es el aislamiento de fallas. Si Nómina deja de estar disponible, Reclutamiento podrá continuar registrando candidatos, Administración de Personal podrá mantener los expedientes y Vacaciones podrá recibir y resolver solicitudes.

Las operaciones que necesiten comunicarse con Nómina permanecerán pendientes hasta que el servicio se recupere.

### 2.1. Comunicación distribuida

Los servicios intercambiarán información mediante dos mecanismos principales:

- **HTTP:** para solicitudes que requieren una respuesta inmediata, como las consultas realizadas desde la interfaz web.
- **Mensajería asíncrona:** para comunicar cambios administrativos entre servicios sin exigir que el destinatario esté disponible en ese momento.

Se utilizará RabbitMQ para gestionar mensajes persistentes, reintentos y mensajes fallidos.

### 2.2. Consistencia de la información

Se aceptará consistencia eventual controlada para determinados procesos administrativos. Por ejemplo, una solicitud de vacaciones podrá quedar aprobada aunque Nómina todavía no haya recibido el evento correspondiente.

Sin embargo, un cálculo definitivo de nómina no podrá confirmarse si depende de información incompleta o desactualizada.

Por tanto, el sistema buscará equilibrar la continuidad operativa con la integridad de los datos económicos.

## 3. Objetivos y lo que no vamos a hacer

### 3.1. Objetivo general

Desarrollar una plataforma distribuida para integrar los procesos esenciales de Recursos Humanos de una PyME, permitiendo administrar información laboral, comunicar cambios entre servicios y recuperarse ante fallas sin comprometer la confiabilidad de los cálculos de nómina.

### 3.2. Objetivos específicos

1. Implementar cuatro servicios independientes: Reclutamiento, Administración de Personal, Vacaciones y Nómina.
2. Establecer una base de datos independiente para cada servicio.
3. Implementar comunicación HTTP y mensajería asíncrona según las necesidades de cada operación.
4. Permitir el registro de candidatos y su incorporación al sistema de personal cuando sean contratados.
5. Administrar expedientes laborales y comunicar cambios relevantes a los servicios correspondientes.
6. Permitir que los empleados soliciten vacaciones y que Recursos Humanos las apruebe o rechace.
7. Calcular nóminas y generar recibos consultables en formato PDF.
8. Implementar autenticación y autorización mediante roles.
9. Conservar mensajes pendientes cuando un servicio no esté disponible.
10. Evitar la ejecución duplicada de operaciones y demostrar mecanismos de recuperación ante fallas.

### 3.3. Funcionalidades incluidas

**Reclutamiento y selección:** registro manual de candidatos, registro interno básico de vacantes, asociación de candidatos con vacantes y seguimiento del estado de selección.

**Administración de Personal:** alta, modificación y baja lógica de empleados; almacenamiento de CURP, RFC, nombre, contacto, puesto, departamento, salario base, fecha de ingreso y estado laboral.

**Vacaciones:** consulta de días disponibles, registro de solicitudes, aprobación o rechazo por Recursos Humanos y actualización de los días disponibles cuando corresponda.

**Nómina:** cálculo de salarios, bonos y deducciones; consulta del historial de nóminas calculadas y generación de recibos en PDF.

**Funciones generales:** inicio de sesión, control de acceso, comunicación entre servicios y supervisión de operaciones pendientes o fallidas.

### 3.4. Funcionalidades excluidas

En la primera versión no se implementarán:

- Transferencias bancarias ni ejecución real de pagos.
- Módulos independientes de beneficios y prestaciones adicionales.
- Formación, desarrollo y capacitación del personal.
- Control automatizado de asistencia.
- Gestión de permisos laborales distintos de las vacaciones.
- Portal público de vacantes y postulaciones automáticas.
- Integración con servicios externos de contratación.
- Replicación avanzada y particionamiento horizontal de bases de datos.

Estas funcionalidades podrán considerarse en versiones posteriores.

## 4. El requisito que perseguimos y lo que dejamos atrás

### 4.1. Requisito prioritario

El requisito prioritario será **garantizar la integridad y confiabilidad de los cálculos definitivos de nómina**, incluso cuando esto implique retrasar su procesamiento.

El equipo considera que un cálculo salarial incorrecto puede ocasionar problemas administrativos y económicos más graves que un retraso temporal.

Por esta razón, Nómina deberá verificar que cuenta con la información laboral necesaria y actualizada antes de confirmar los resultados.

### 4.2. Disponibilidad de funciones independientes

Al mismo tiempo, se buscará que una falla de Nómina no interrumpa los procesos independientes de Reclutamiento, Administración de Personal y Vacaciones.

Los cambios que necesiten comunicarse a un servicio temporalmente inactivo se conservarán como operaciones pendientes.

### 4.3. Sacrificios aceptados

El equipo acepta que esta arquitectura implica:

- Mayor complejidad de desarrollo.
- Necesidad de administrar varios servicios y bases de datos.
- Recursos adicionales para mensajería, gateway y contenedores.
- Posibles retrasos en la sincronización de información.
- Mayor esfuerzo de pruebas y mantenimiento.

Se considera que estos costos pueden justificarse por la independencia de los servicios, la capacidad de recuperación y la posibilidad de escalar componentes específicos.

No obstante, la infraestructura inicial deberá mantenerse proporcional a las necesidades de una PyME y al alcance académico del proyecto.

## 5. Historias de usuario

### HU-01. Registro y seguimiento de candidatos

**Como** integrante del área de Reclutamiento, **quiero** registrar candidatos y asociarlos con vacantes internas **para** dar seguimiento al proceso de selección.

**Criterios de aceptación:**

- El sistema permite registrar un candidato con sus datos obligatorios.
- El candidato puede asociarse con una vacante interna.
- Es posible actualizar su estado durante el proceso de selección.
- Cuando se registra su contratación, Reclutamiento genera un evento destinado a Administración de Personal.
- Si Administración de Personal no está disponible, el evento permanece pendiente hasta su procesamiento.

### HU-02. Administración de expedientes laborales

**Como** responsable de Recursos Humanos, **quiero** registrar y actualizar empleados **para** mantener sus expedientes laborales completos y actualizados.

**Criterios de aceptación:**

- El sistema exige los datos obligatorios al registrar un empleado.
- No permite duplicar identificadores únicos, como CURP o RFC.
- Permite modificar puesto, departamento, salario base y otros datos autorizados.
- Los cambios relevantes se comunican a Nómina y Vacaciones mediante eventos.
- La baja de un empleado modifica su estado laboral sin eliminar su historial.

### HU-03. Solicitud y autorización de vacaciones

**Como** empleado, **quiero** consultar mis días disponibles y solicitar vacaciones indicando las fechas **para** gestionar mis periodos de descanso.

**Como** responsable de Recursos Humanos, **quiero** aprobar o rechazar las solicitudes **para** considerar las necesidades operativas de la empresa.

**Criterios de aceptación:**

- El empleado solo puede solicitar vacaciones correspondientes a su propio expediente.
- La solicitud debe incluir las fechas correspondientes.
- El sistema valida que no se excedan los días disponibles.
- Las solicitudes nuevas quedan en estado pendiente.
- Recursos Humanos puede aprobarlas o rechazarlas.
- Al aprobar una solicitud, se actualiza el saldo de días disponibles y se publica un evento para Nómina.
- Al rechazarla, se registra el motivo y no se descuentan días.

### HU-04. Cálculo y consulta de nómina

**Como** responsable autorizado de Recursos Humanos, **quiero** calcular la nómina de un periodo **para** obtener resultados confiables y generar recibos.

**Criterios de aceptación:**

- El sistema utiliza los datos laborales vigentes.
- Considera los conceptos de salario, bonos y deducciones aplicables.
- Verifica que los movimientos relevantes hayan sido sincronizados.
- No confirma cálculos que dependan de información incompleta.
- Los empleados con información pendiente no se incluyen en resultados definitivos hasta completar su validación.
- Una nómina confirmada conserva su resultado histórico y no puede modificarse directamente.
- Se genera un recibo en PDF que el empleado autorizado puede consultar y descargar.

### HU-05. Recuperación de mensajes pendientes

**Como** administrador, **quiero** que los eventos pendientes se procesen automáticamente después de una falla **para** evitar pérdidas de información y reducir la intervención manual.

**Criterios de aceptación:**

- Al detener Nómina, Vacaciones puede continuar aprobando solicitudes.
- Los eventos correspondientes permanecen en una cola persistente.
- Al reiniciar Nómina, los eventos pendientes se procesan automáticamente.
- Un evento recibido dos veces no genera dos movimientos.
- Si un evento supera el máximo de reintentos, se envía a una cola de mensajes fallidos.
- El administrador puede identificar los eventos que requieren revisión.

### HU-06. Control de acceso

**Como** administrador general, **quiero** asignar permisos según el rol de cada usuario **para** proteger la información personal y laboral.

**Criterios de aceptación:**

- Los usuarios deben autenticarse para acceder a funciones protegidas.
- El administrador puede gestionar usuarios y permisos.
- Recursos Humanos puede acceder a las operaciones autorizadas de su área.
- Un empleado únicamente puede consultar sus propios recibos y solicitudes.
- Un empleado no puede aprobar vacaciones ni consultar información salarial ajena.
- Las solicitudes no autorizadas son rechazadas por la API, incluso si se realizan directamente sin utilizar la interfaz web.

## 6. Del monolito a los servicios

### 6.1. Diseño monolítico inicial

Como punto de partida conceptual, SIGRH podría desarrollarse como una única aplicación que contuviera cuatro módulos internos: Reclutamiento, Administración de Personal, Vacaciones y Nómina.

Estos módulos se comunicarían mediante llamadas locales a funciones o clases y utilizarían una infraestructura de datos compartida.

Aunque esta alternativa permitiría simplificar el desarrollo inicial, también concentraría las responsabilidades y recursos de ejecución en una misma aplicación.

Si los cálculos de nómina consumieran una cantidad importante de memoria o capacidad de procesamiento, podrían afectar el rendimiento de los demás módulos. Asimismo, ciertas actualizaciones podrían requerir desplegar nuevamente la aplicación completa.

Esto no significa que todos los monolitos presenten necesariamente esos problemas, sino que son riesgos que el equipo busca reducir mediante la separación propuesta.

### 6.2. Separación en cuatro servicios

**Servicio de Reclutamiento:** administra candidatos, vacantes internas y estados de selección. Cuando una persona es contratada, publica un evento para iniciar la creación de su expediente laboral.

**Servicio de Administración de Personal:** administra altas, modificaciones, bajas lógicas y expedientes de empleados. Es la fuente oficial de los datos laborales y comunica los cambios relevantes a Nómina y Vacaciones.

**Servicio de Vacaciones:** administra los días disponibles, las solicitudes y sus autorizaciones. Cuando se aprueban vacaciones, actualiza sus registros y comunica el evento a Nómina.

**Servicio de Nómina:** realiza los cálculos salariales, administra el historial de resultados y genera recibos en PDF. Consume los eventos que modifican la información necesaria para sus operaciones.

### 6.3. Propiedad de los datos

Cada servicio tendrá una base de datos PostgreSQL independiente.

Ningún servicio accederá directamente a las tablas de otro. La información se intercambiará mediante las interfaces HTTP o los eventos publicados en RabbitMQ.

Esta decisión busca reducir el acoplamiento entre los servicios y permitir cambios independientes en sus estructuras de datos.

### 6.4. Comunicación entre servicios

Se utilizará comunicación asíncrona para los cambios de estado relevantes:

- Reclutamiento → Administración de Personal: contratación de candidatos.
- Administración de Personal → Nómina: altas y modificaciones de datos laborales.
- Administración de Personal → Vacaciones: altas y cambios relevantes de empleados.
- Vacaciones → Nómina: vacaciones aprobadas.

Las solicitudes de los usuarios que requieran una respuesta inmediata utilizarán HTTP a través del API Gateway.

### 6.5. Manejo de información desactualizada

Antes de confirmar un cálculo definitivo, Nómina deberá verificar que utiliza la versión vigente de los datos laborales necesarios.

Si detecta información desactualizada, intentará sincronizarla. Si no puede completar la verificación dentro del tiempo establecido, el cálculo afectado quedará pendiente y se notificará al administrador.

Se propone utilizar identificadores de versión o actualización para reconocer cambios en los registros laborales.

## 7. Dónde vive cada pieza

| Pieza | Dónde vive | Por qué | Qué pasa si falla |
|---|---|---|---|
| API Gateway | Componente de entrada HTTP, delante de los servicios | Centraliza el enrutamiento y la validación inicial de autenticación | Las nuevas solicitudes de los usuarios pueden quedar temporalmente inaccesibles; los procesos internos independientes pueden continuar |
| Directorio y DNS | DNS interno de la red de Docker Compose | Permite localizar servicios por sus nombres sin depender de direcciones IP fijas | Las nuevas conexiones entre servicios pueden fallar; se aplican tiempos de espera y recuperación según corresponda |
| Balanceo de carga | Proxy inverso Nginx para solicitudes HTTP y consumidores de RabbitMQ para trabajos asíncronos | Permite distribuir solicitudes y tareas cuando aumenta la demanda | Se reduce la capacidad de procesamiento; si el proxy falla completamente, las rutas que dependen de él dejan de responder |
| Datos y particionamiento | Bases de datos PostgreSQL independientes por servicio | Mantiene la propiedad de los datos y reduce el acoplamiento | El servicio afectado puede quedar temporalmente limitado; no se implementará sharding ni replicación avanzada inicialmente |
| Operaciones entre servicios | Lógica de cada servicio y eventos gestionados mediante RabbitMQ | Permite coordinar procesos sin exigir disponibilidad simultánea | Las operaciones permanecen pendientes; se aplican reintentos y, cuando corresponda, revisión administrativa |
| Manejo de fallas | Timeouts y circuit breakers en clientes HTTP; reintentos, persistencia y DLQ en mensajería | Evita esperas indefinidas, pérdida de eventos y propagación innecesaria de fallas | Se registra el problema, se limita la operación afectada y se intenta recuperar el procesamiento |

### 7.1. Mensajería y orden de procesamiento

RabbitMQ será el intermediario de mensajes entre servicios.

Se utilizarán colas persistentes y mecanismos de confirmación de procesamiento. Para los eventos que necesiten un orden estricto, como los movimientos de un mismo empleado, deberá mantenerse una estrategia de consumo que respete dicho orden.

El uso de una cola FIFO no se considerará suficiente por sí solo para garantizar ausencia de duplicados o consistencia entre servicios.

### 7.2. Reintentos y mensajes fallidos

Se propone permitir un intento inicial y hasta tres reintentos automáticos, con esperas progresivas de 5, 15 y 45 segundos.

Cuando un mensaje no pueda procesarse después de los intentos permitidos, se enviará a una *Dead Letter Queue* (DLQ).

El administrador podrá revisar el identificador del mensaje, el error y los intentos realizados.

### 7.3. Idempotencia

Cada evento tendrá un identificador único.

Antes de aplicar una actualización, el servicio consumidor comprobará si ya procesó ese identificador. Si el evento ya fue atendido, no repetirá el movimiento.

El registro del evento procesado y la actualización de los datos deberán ejecutarse dentro de una misma transacción local cuando corresponda.

### 7.4. Timeouts y circuit breaker

Las solicitudes HTTP internas tendrán inicialmente un tiempo máximo configurable de cinco segundos.

También se propone utilizar un *Circuit Breaker* que se abra después de cinco fallas consecutivas y permita una prueba de recuperación después de treinta segundos.

Estos valores son configuraciones iniciales sujetas a validación durante las pruebas.

### 7.5. Coordinación de operaciones

Se priorizarán operaciones pendientes y consistencia eventual controlada en lugar de implementar sagas completas para todos los procesos.

Cuando una operación afecte a dos servicios, cada uno realizará su transacción local y comunicará los cambios necesarios mediante eventos.

Se contempla el patrón *Transactional Outbox* para reducir el riesgo de guardar una modificación sin publicar su evento correspondiente.

Las operaciones que requieran compensaciones deberán identificarse de manera específica durante la implementación.

### 7.6. Seguridad

Se utilizará autenticación de usuarios y autorización basada en roles (RBAC).

El API Gateway verificará inicialmente las credenciales o tokens, mientras que los servicios deberán validar los permisos particulares de cada operación.

Se aplicará el principio de mínimo privilegio y se protegerá la información sensible, especialmente los expedientes laborales y los datos de nómina.

## 8. Stack y cómo se levanta

### 8.1. Tecnologías seleccionadas

| Componente | Tecnología |
|---|---|
| Backend | Python y Flask |
| Frontend | HTML y tecnologías web básicas |
| Comunicación síncrona | HTTP y API REST |
| Comunicación asíncrona | RabbitMQ |
| Base de datos | PostgreSQL |
| API Gateway y proxy inverso | Nginx |
| Contenedores | Docker |
| Orquestación local | Docker Compose |
| Control de versiones | Git y GitHub |
| Recibos de nómina | Archivos PDF |

### 8.2. Organización de contenedores

El proyecto utilizará un archivo `compose.yaml` para definir los componentes necesarios.

Se contemplan contenedores para:

- Frontend web.
- API Gateway.
- Servicio de Reclutamiento.
- Servicio de Administración de Personal.
- Servicio de Vacaciones.
- Servicio de Nómina.
- RabbitMQ.
- Almacenamiento PostgreSQL con bases de datos separadas por servicio.

Los componentes se comunicarán mediante una red interna de Docker Compose. Los datos persistentes deberán conservarse mediante volúmenes.

La separación lógica de bases de datos no implica necesariamente ejecutar un servidor PostgreSQL diferente por cada servicio en la primera versión.

### 8.3. Ejecución

El procedimiento previsto para levantar el sistema será:

1. Clonar el repositorio del proyecto desde GitHub.
2. Acceder al directorio que contiene el archivo `compose.yaml`.
3. Configurar las variables de entorno requeridas.
4. Ejecutar `docker compose up --build -d`.
5. Verificar que los contenedores estén activos.
6. Acceder a la interfaz web desde el navegador.
7. Iniciar sesión con usuarios de prueba.
8. Comprobar las operaciones principales de los cuatro servicios.

### 8.4. Pruebas de funcionamiento

Se realizarán pruebas funcionales de registro de candidatos, administración de empleados, solicitudes de vacaciones, cálculos de nómina y generación de recibos PDF.

También se realizarán pruebas de fallas distribuidas.

Una prueba consistirá en detener temporalmente el servicio de Nómina, aprobar una solicitud de vacaciones y comprobar que el evento permanezca en RabbitMQ. Después se reiniciará Nómina y se verificará que el mensaje se procese sin intervención manual.

Otra prueba enviará dos veces un evento con el mismo identificador para comprobar que únicamente se aplique un movimiento.

Finalmente, se probarán las restricciones de acceso utilizando usuarios con distintos roles y realizando solicitudes directamente a la API.

## 9. Decisiones de arquitectura

| Decisión | Alternativas descartadas o pospuestas | Justificación |
|---|---|---|
| Cuatro servicios independientes | Un único servicio para todo Recursos Humanos | Permite separar responsabilidades, aislar fallas y escalar componentes específicos |
| Reclutamiento separado de Personal | Unificar candidatos y empleados | Un candidato no necesariamente se convierte en empleado |
| Base de datos por servicio | Base de datos compartida con acceso directo | Reduce el acoplamiento y mantiene la propiedad de los datos |
| RabbitMQ para eventos | Depender únicamente de llamadas HTTP | Permite conservar operaciones pendientes durante fallas temporales |
| HTTP para consultas inmediatas | Utilizar mensajería para todas las operaciones | Simplifica las interacciones que requieren respuesta directa |
| Consistencia eventual controlada | Exigir sincronización inmediata para todos los cambios | Permite mantener disponibles operaciones administrativas independientes |
| Validación antes de confirmar nómina | Calcular inmediatamente con información potencialmente incompleta | Prioriza la confiabilidad de los resultados económicos |
| Reintentos y DLQ | Reintentos ilimitados o descarte inmediato | Facilita la recuperación y permite revisar fallas persistentes |
| Idempotencia por identificador de evento | Suponer que cada mensaje se entrega una sola vez | Evita aplicar movimientos duplicados |
| Operaciones pendientes como estrategia principal | Sagas completas para todas las operaciones | Reduce la complejidad inicial y permite recuperación posterior |
| Docker Compose | Orquestación más compleja desde la primera versión | Es suficiente para el entorno académico y facilita la reproducción del sistema |
| Sin sharding ni replicación avanzada inicial | Distribución y replicación complejas desde el inicio | Mantiene el alcance proporcional a una PyME y al proyecto |
| Autorización basada en roles | Acceso uniforme a todas las funciones | Protege los expedientes y la información salarial |
| Sin transferencias bancarias | Integración inmediata con bancos | Limita riesgos y concentra el desarrollo en el cálculo y registro de nómina |

## 10. Preguntas abiertas y desacuerdos

### 10.1. Acuerdos del equipo

Los integrantes del equipo manifestaron estar de acuerdo con la orientación general del proyecto, la separación en cuatro servicios, las prioridades de consistencia y disponibilidad, el uso de comunicación asíncrona y la necesidad de implementar mecanismos de recuperación ante fallas.

Durante la discusión existieron diferencias menores de opinión que fueron resueltas mediante el intercambio de argumentos.

No se reportan desacuerdos activos sobre las decisiones principales de la arquitectura.

### 10.2. Aspectos sujetos a validación técnica

Aunque no existen desacuerdos pendientes, algunos detalles de implementación deberán verificarse mediante pruebas:

- El comportamiento del orden FIFO al utilizar varios consumidores.
- La configuración definitiva de tiempos de espera y reintentos.
- La implementación concreta de la publicación confiable de eventos.
- La estrategia de validación de versiones de datos antes de confirmar la nómina.
- El alcance del balanceo de carga en la demostración académica.
- La configuración de respaldos y recuperación de bases de datos.

Estos puntos representan detalles técnicos por validar, no desacuerdos entre los integrantes.

### 10.3. Compromiso de implementación

El equipo se propone demostrar el funcionamiento de los cuatro servicios principales, la gestión de información de Recursos Humanos, la comunicación entre componentes, la generación de recibos de nómina en PDF y la autenticación basada en roles.

También se buscará demostrar la continuidad de operaciones independientes cuando Nómina no esté disponible, el procesamiento de mensajes pendientes y la prevención de movimientos duplicados.

El objetivo es presentar un sistema funcional cuyo comportamiento distribuido pueda comprobarse mediante pruebas reproducibles.

---

**Fin del documento PRD — SIGRH.**
