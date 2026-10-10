# PRD · The Four Nodes — búsqueda comunitaria de mascotas

**Equipo:** The Four Nodes  
**Integrantes:** [@Montiel-Oscar](https://github.com/Montiel-Oscar) (Oscar Ivan Montiel Juarez), [@LuisaCedeno](https://github.com/LuisaCedeno) (Luisa María Segura Cedeño), [@CeciliaXimenaSolisCisneros](https://github.com/CeciliaXimenaSolisCisneros) (Cecilia Ximena Solis Cisneros) y [@Amyv19](https://github.com/Amyv19) (Amy Valentina Veraza García).  
**Fecha:** 9 de octubre de 2026.  
**Alcance:** primera versión funcional para demostración local durante un mes.

## 1. Problema y usuarios

La persona que pierde una mascota suele colocar carteles cerca de donde se extravió y preguntar a quienes pasan por la zona. Pocas personas cuentan con un localizador. Este método depende de que alguien vea el cartel a tiempo y tiene poco alcance fuera de las calles cercanas.

El usuario principal es quien perdió una mascota: necesita publicar pronto y llegar a personas de la zona. El usuario secundario es quien encontró o vio una mascota: puede publicar un hallazgo. Cualquier visitante puede consultar y compartir reportes públicos. El valor del sistema es ampliar el alcance de la búsqueda mediante enlaces compartibles y avisar pronto a quienes siguen un municipio; no presupone una carga masiva de usuarios.

## 2. Por qué es un sistema distribuido

El sistema comienza como monolito. Después se separan **identidad**, **reportes** y **alertas** para estudiar responsabilidades, comunicación y fallas entre procesos. La frontera importante es entre reportes y alertas: publicar un extravío no debe esperar a que se generen avisos ni fallar porque alertas esté detenido. Identidad se separa al final para que las credenciales tengan dueño propio y los otros servicios puedan comprobar localmente la identidad mediante un token firmado.

El alcance por internet, por sí solo, no exige microservicios. Esta partición permite cumplir la prioridad del producto y demostrar los temas del curso con tres servicios pequeños. Si el número de usuarios crece diez veces, se pueden añadir copias de `reportes` y medir la cola de eventos; la única instancia PostgreSQL será un límite reconocido antes de prometer más capacidad.

## 3. Objetivos y lo que no vamos a hacer

La primera versión tendrá cuatro funciones:

1. **Registrar mascotas:** guardar una ficha breve para reutilizarla. También se podrá crear la ficha al publicar un extravío.
2. **Publicar extravíos:** registrar descripción, fecha y zona de la última vez que se vio a la mascota; generar una página pública con enlace compartible.
3. **Registrar hallazgos:** publicar una descripción y la zona del avistamiento sin tener que relacionarlo con un extravío concreto.
4. **Recibir alertas:** seguir un municipio y consultar en una bandeja web los nuevos extravíos y hallazgos de esa zona.

Los reportes públicos se podrán filtrar por municipio. La zona se expresará con municipio, colonia y una referencia textual visible, por ejemplo un cruce de calles o un lugar público. El formulario pedirá no escribir un domicilio particular. Quien publica podrá añadir voluntariamente un teléfono de contacto público; si lo omite, la otra persona aún podrá publicar un hallazgo que el dueño verá en la bandeja si sigue ese municipio.

El registro e inicio de sesión con correo y contraseña son funciones de apoyo para publicar y suscribirse; se mantienen deliberadamente básicos. Antes de publicar un teléfono, el formulario pide consentimiento explícito para mostrarlo.

La versión se demostrará localmente con Docker Compose. Quedan fuera: mapa interactivo, coordenadas, localizadores o chips, reconocimiento por foto, carga de imágenes, chat, vinculación automática de hallazgos, correo, notificaciones push, recuperación de contraseña y despliegue público. Una interfaz web sencilla, adaptable a teléfono, basta para demostrar los flujos.

## 4. El requisito que perseguimos y lo que dejamos atrás

**Prioridad:** una publicación válida se guarda y confirma de inmediato, aunque alertas esté caído. En operación normal de la demostración local, con hasta 20 personas suscritas al municipio y los servicios saludables, la alerta debe crearse en un máximo de **2 minutos y 30 segundos** desde la confirmación del reporte. La web abierta consulta la bandeja al menos cada 30 segundos, por lo que debe mostrarla en un máximo de **3 minutos**. El sistema mide ambos tiempos desde la creación del reporte. Con la web cerrada, la alerta queda guardada y se ve en la siguiente visita; esta versión no promete un aviso en el dispositivo.

Si alertas falla, se acepta superar los 3 minutos: el evento pendiente persiste y se procesa al recuperarse. Hay **consistencia eventual** entre reportes y alertas. No se deshace un reporte válido por una falla posterior de alertas. Tampoco se promete disponibilidad si cae la única base de datos o el gateway de esta demostración local.

## 5. Historias de usuario

### 5.1 Publicar un extravío

**Como** persona que perdió una mascota, **quiero** publicar su descripción y última zona conocida **para** pedir ayuda a la comunidad.

- Con una cuenta iniciada, puedo elegir una mascota registrada o crear su ficha en el mismo formulario.
- Capturo nombre o descripción identificable, especie, rasgos, fecha aproximada, municipio, colonia y referencia pública del lugar. El teléfono de contacto es opcional y solo se muestra si acepto hacerlo público.
- Recibo un identificador y un enlace público solo después de que el reporte se guardó. Un reintento con la misma clave de idempotencia devuelve el mismo reporte.
- Si alertas está detenido, el reporte sigue visible y el evento para avisar queda pendiente.

### 5.2 Registrar una mascota

**Como** dueño, **quiero** guardar una ficha breve **para** reutilizarla al reportar un extravío.

- La ficha contiene nombre, especie y rasgos. Solo su dueño puede modificarla.
- Si creo ficha y extravío juntos, ambos se guardan o ninguno se guarda.
- Cambiar la ficha después no altera la descripción ya publicada en un reporte.

### 5.3 Registrar un hallazgo

**Como** persona que vio o encontró una mascota, **quiero** publicar dónde y cuándo ocurrió **para** que su dueño pueda buscarla.

- Con una cuenta iniciada, capturo descripción, fecha aproximada, municipio, colonia y referencia pública. El teléfono de contacto es opcional.
- El hallazgo recibe enlace público, aparece en la lista del municipio y no requiere identificar un extravío previo.
- El sistema no afirma que dos reportes correspondan a la misma mascota.

### 5.4 Recibir alertas

**Como** persona interesada en una zona, **quiero** seguir un municipio **para** enterarme de nuevos reportes de esa zona.

- Con una cuenta iniciada, puedo seguir un municipio y dejar de seguirlo.
- Un extravío o hallazgo nuevo genera a lo sumo una alerta por evento y suscriptor existente al momento de publicarlo.
- En condiciones normales, la alerta queda en la bandeja antes de 3 minutos. Si alertas se detiene y vuelve, el evento se procesa una sola vez desde la perspectiva del usuario.

## 6. Del monolito a los servicios

| Rama | Resultado comprobable |
|---|---|
| `01-monolito` | Una aplicación y una base. Publicación completa de extravío con captura de mascota en el mismo formulario y cuenta de demostración. Módulos internos: identidad, mascotas, reportes y alertas. |
| `02-separacion` | Se completan las cuatro funciones y el registro e inicio de sesión básicos; `alertas` pasa a ser otro proceso y otra base lógica. Publicar sigue funcionando con ese proceso apagado. |
| `03-identidad-gateway` | `identidad` pasa a servicio propio. Traefik queda como única entrada; los servicios se encuentran por DNS de Compose y Traefik reparte peticiones entre dos copias de `reportes`. |
| `04-resiliencia` | Se verifican reintentos, deduplicación, tiempos de alerta y caídas de procesos con pruebas reproducibles. |

En el sistema final, **identidad** posee cuentas, hashes de contraseñas y emisión de tokens; **reportes** posee fichas de mascotas, extravíos, hallazgos, ubicaciones y eventos pendientes; **alertas** posee suscripciones, alertas e intentos de procesamiento. Cada uno escribe solo en su base lógica. Los otros servicios conservan únicamente el identificador del usuario y verifican localmente la firma y vigencia del token; publicar no hace una llamada sincrónica a identidad.

Al confirmar un reporte, `reportes` guarda en **una transacción local** el reporte y un evento pendiente con ID único, tipo, municipio y fecha. `alertas` consulta periódicamente esos eventos por HTTP interno, crea en una transacción local las alertas para quienes seguían ese municipio al publicarse el evento y después confirma el procesamiento. Si falla la creación, no confirma el evento y lo vuelve a intentar. Si se pierde la confirmación, el evento puede llegar otra vez; una restricción única por **ID de evento + ID de destinatario** impide alertas duplicadas. No hay escritura directa entre bases ni transacción distribuida que abarque ambos servicios.

## 7. Dónde vive cada pieza

| Pieza | Dónde vive | Por qué | Qué pasa si falla |
|---|---|---|---|
| API gateway | Traefik es la única puerta pública. Dirige `/` a la interfaz servida por reportes, `/api/usuarios/` a identidad, `/api/mascotas/` y `/api/reportes/` a reportes, y `/api/alertas/` a alertas. | Da una entrada y rutas claras. | No entran nuevas solicitudes mientras está caído. Los datos ya guardados permanecen; es un punto único de falla aceptado para la demo. |
| Directorio / DNS | DNS interno de Docker Compose para llamadas entre servicios; Traefik descubre contenedores mediante su proveedor Docker. | Evita direcciones IP fijas. | Si Docker o su DNS falla, los procesos no se encuentran; se restablece el entorno y se comprueban rutas antes de continuar. |
| Balanceo | Traefik reparte peticiones entre dos copias sin estado de `reportes`; ambas usan la misma base de reportes. | Permite observar una réplica caída y continuar con la otra. | Durante la detección pueden fallar peticiones. El cliente reintenta publicaciones solo con clave de idempotencia; si caen ambas, no se confirma el reporte. |
| Datos | Una instancia PostgreSQL con tres bases lógicas y credenciales separadas, una por servicio. Sin réplicas ni particiones en esta versión. | Permite propiedad de datos y transacciones locales sin operar un clúster de base de datos. | Si PostgreSQL cae, no se confirman nuevas escrituras. La base es un punto único de falla declarado; no se presume alta disponibilidad. |
| Operaciones entre servicios | Eventos pendientes en la base de `reportes`; `alertas` los consulta por HTTP interno y confirma solo después de guardar sus alertas. | La publicación no depende de una transacción distribuida ni de que alertas esté disponible. | Si alertas falla, el evento queda pendiente. No se revierte el reporte y no hace falta una saga compensatoria para este flujo. |
| Manejo de fallas | Consultas internas con timeout de 2 segundos y reintentos con espera; clave de idempotencia para publicar; alerta única por evento y destinatario. | Evita esperas indefinidas y duplicados en reintentos. | La siguiente consulta retoma el trabajo. Una publicación sin confirmación solo se reintenta con la misma clave. No se usa circuit breaker porque publicar no llama sincrónicamente a otro servicio. |

## 8. Stack y cómo se levanta

Python y FastAPI se usan en el monolito y los tres servicios para mantener un solo lenguaje en la API y el proceso que genera alertas. PostgreSQL permite guardar reporte y evento en una transacción local y aplicar restricciones de unicidad. Traefik aporta gateway, descubrimiento de contenedores y balanceo. Docker Compose levanta los servicios, la base y la red interna. La interfaz usa HTML, CSS y JavaScript sencillos; no necesita una aplicación móvil independiente.

`docker compose up --build` debe levantar el sistema y mostrar una página inicial desde Traefik. La demostración mínima debe permitir: publicar un extravío; ver su enlace público; recibir la alerta en menos de 3 minutos; detener `alertas` y publicar otro sin perderlo; reiniciar `alertas` y recibir una sola alerta; detener una copia de `reportes` y continuar con la otra; detener PostgreSQL y comprobar que no aparece como publicado un reporte que no se guardó.

## 9. Decisiones de arquitectura

| Decisión | Alternativa descartada para esta versión | Por qué |
|---|---|---|
| Empezar con un monolito y separar por ramas. | Crear todos los servicios desde el primer día. | Primero se comprueba el flujo y después se hace visible el efecto de cruzar la red. |
| Conservar reportes y eventos pendientes en la misma transacción local. | Enviar alertas dentro de la solicitud de publicación. | Una falla de alertas no impide guardar el reporte. |
| Separar tres servicios: identidad, reportes y alertas. | Dividir también mascotas y geolocalización. | Hay tres fronteras defendibles sin multiplicar llamadas y bases. |
| Usar tokens firmados que reportes y alertas validan localmente. | Consultar identidad en cada solicitud. | Publicar con un token vigente sigue funcionando si identidad se detiene. Se acepta que un token siga válido hasta su vencimiento. |
| Usar bandeja web y suscripción por municipio. | Correo, push y avisos por coordenadas. | El canal y la zona son comprobables en una demo local sin infraestructura adicional. |
| Representar ubicación con municipio, colonia y referencia pública. | Mapa y coordenadas. | Permite describir el avistamiento sin implementar cartografía; se evita pedir domicilios privados. |
| Usar una instancia PostgreSQL con bases separadas. | Réplicas y particiones sin carga medida. | La prioridad es demostrar propiedad de datos y fallas entre servicios. Se reconoce la falla de la base única. |
| Usar consulta periódica de eventos por HTTP interno. | Añadir un broker desde el inicio. | Da entrega diferida recuperable con menos componentes para operar en un mes. |

## 10. Preguntas abiertas y desacuerdos

No hubo registros ni desacuerdos.
