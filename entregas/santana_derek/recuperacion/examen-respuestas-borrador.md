# Examen escrito · borrador de apoyo para revisar

Preparado con asistencia de Codex el 7 de noviembre de 2026. No es una entrega
manuscrita ni acredita comprensión personal. Revisar, explicar con palabras propias
y escribir a mano antes de escanear. No se ha producido `examen-escrito.pdf`.

Enunciado: [guía oficial](https://github.com/OscarRuiz21/sd-2027-1/blob/main/examen-escrito/README.md).
Fecha límite: **21 de noviembre de 2026, antes de las 06:59**.
Son las diez fijas y quince elegidas de nueve temas distintos; no se incluyen los
dos ejemplos resueltos. Cada párrafo está pensado como base para 4–6 líneas a mano;
ajustar la extensión conservando la explicación y el costo de la decisión.

## Diez preguntas fijas

### 1.7 · Monolito y microservicios
Un monolito despliega sus módulos como una unidad; los microservicios separan capacidades de negocio en servicios desplegables de forma independiente. Ambos pueden tener varias copias. El monolito facilita transacciones, pruebas y operación para un equipo pequeño. Los microservicios permiten escalar y cambiar una capacidad por separado, a cambio de red, fallas parciales y coordinación de datos. Empezaría modular y separaría solo donde exista una necesidad concreta de autonomía o carga.

### 2.4 · Idempotencia al cobrar
Una operación idempotente puede repetirse sin añadir otro efecto de negocio. El cliente conserva una llave por intento lógico de compra y el servidor registra llave, datos del cobro y resultado, con unicidad y coordinación atómica. Una repetición devuelve el resultado previo; la misma llave con datos distintos se rechaza. Al llamar a una pasarela también envío su llave de idempotencia y reconcilio resultados ambiguos. Gano reintentos seguros, pero debo conservar estado y definir la vigencia de las llaves.

### 2.5 · Timeout de la pasarela
El timeout solo dice que mi servicio no obtuvo una respuesta dentro de su plazo. La pasarela pudo no recibir el cobro, estar procesándolo o haber cobrado y perdido la respuesta. Marco el pago como pendiente de verificación y consulto su estado por identificador; si reintento, uso la misma llave. No afirmo que falló ni genero otro cobro independiente. Esto evita duplicar dinero, aunque obliga a mostrar incertidumbre temporal y reconciliar después.

### 3.6 · Gateway y balanceador
Un balanceador reparte tráfico entre instancias y evita enviar solicitudes a destinos detectados como caídos. Un API gateway ofrece una entrada de API: enruta por recurso y puede aplicar autenticación, límites y adaptación de protocolos. Un mismo producto puede hacer ambas tareas, pero sus responsabilidades son distintas. Pondría balanceo para varias copias y gateway para una API compuesta por servicios. Ganamos control central; pagamos otro salto y debemos evitar que esa entrada sea un punto único de falla.

### 4.1 · CAP
CAP dice que, durante una partición de red, no se pueden garantizar simultáneamente linealizabilidad y respuesta de toda petición a cada nodo que sigue funcionando. Para preservar consistencia, algunas operaciones deben esperar o rechazarse; para seguir aceptándolas en ambos lados pueden aparecer lecturas viejas o conflictos. No es escoger dos propiedades para siempre: la decisión afecta operaciones durante la partición. En saldos priorizaría reglas consistentes; en un catálogo podría aceptar datos viejos, con mecanismos de reconciliación.

### 5.3 · Saga y commit en dos fases
El commit en dos fases prepara a todos los participantes y después comunica commit o abort; coordina una decisión atómica, pero puede retener recursos y quedar bloqueado ante fallas del coordinador. Una saga encadena transacciones locales y compensa efectos si un paso falla. Mantiene autonomía y facilita procesos largos, pero expone estados intermedios y no ofrece aislamiento global. Usaría 2PC con recursos compatibles y coordinación controlada; una saga para pedidos entre servicios si existen compensaciones de negocio viables.

### 6.3 · Cola, pub/sub y log
En una cola punto a punto, consumidores de una misma cola compiten por cada mensaje: sirve para repartir trabajo. En pub/sub, cada suscripción interesada recibe su copia: sirve para que pago, correo y análisis reaccionen al mismo evento. Un log retiene registros ordenados por partición y cada grupo lleva su posición, permitiendo releer y reconstruir vistas. Gano desacoplamiento o replay según el modelo, pero debo gestionar duplicados, retención y retrasos; no existe necesariamente orden global.

### 6.4 · Entrega y efecto exactamente una vez
Si el consumidor aplica un efecto y cae antes del ack, el broker no sabe si terminó: reenviar puede duplicar y no reenviar puede perder trabajo. Por eso un broker aislado no garantiza una única entrega y un único efecto arbitrario en sistemas externos. Puedo buscar entrega al menos una vez y efectos idempotentes: guardar identificador y cambio en una transacción, y confirmar después. Hay garantías exactamente una vez dentro de ámbitos transaccionales específicos, pero no se extienden solas a una pasarela externa.

### 7.2 · Sucedió antes y Lamport
Un evento sucede antes que otro si lo precede en el mismo proceso, si es el envío del mensaje que el otro recibe, o por transitividad. Lamport incrementa su contador en cada evento; al recibir un valor remoto usa máximo(local, recibido) más uno. Así, si a sucedió antes que b, L(a) es menor que L(b). La implicación inversa no vale: números diferentes no prueban causalidad. Es un orden lógico barato; para detectar concurrencia necesito información adicional, como relojes vectoriales.

### 8.4 · Cuándo usar caché
Pondría una caché cuando muchas lecturas repiten datos, la base es un cuello de botella y puedo definir cuánto retraso tolero. Gano latencia y menor carga, pero agrego invalidación, expiración y otra dependencia. No usaría una copia potencialmente vieja para decidir un cargo, disponibilidad estricta o una autorización que deba revocarse inmediatamente: consultaría la fuente adecuada y validaría de forma atómica. No hay una prohibición universal por tipo de dato; lo decisivo es que la garantía de la caché sea suficiente para esa decisión.

## Quince preguntas elegidas

### 1.5 · Falla parcial
Hay falla parcial cuando una parte deja de responder mientras otras siguen operando, y además no sabemos de inmediato si murió o solo está lenta o aislada. Un programa local normalmente comparte proceso y dominio de falla, sin esa incertidumbre de comunicación entre máquinas; también puede sufrir fallos de componentes. Al distribuir debo usar plazos, estados pendientes y recuperación. Puedo mantener funciones sanas disponibles, pero ya no debo interpretar un timeout como prueba de que una operación no ocurrió.

### 1.8 · Escalar un monolito sin estado
Un monolito sin estado sí puede escalar copiándose diez veces detrás de un balanceador. Los microservicios permiten copiar solamente el componente costoso, por ejemplo búsqueda, sin multiplicar también usuarios y pedidos. También pueden desplegarse por separado y elegir recursos distintos. Esto ahorra recursos cuando las cargas son desiguales, pero añade llamadas remotas y operación. No separaría servicios solo para tener réplicas: primero mediría cuál componente requiere escala independiente.

### 2.6 · Dos cobros simultáneos con la misma llave
El servidor debe reclamar la llave de manera atómica mediante una restricción única o mecanismo equivalente, no con un consultar-y-luego-insertar sin protección. Solo el ganador inicia el efecto; el otro encuentra en curso la operación y espera con límite o devuelve pendiente. Una vez terminada, ambos reciben el mismo resultado; si sus datos difieren, se rechaza el conflicto. El estado debe sobrevivir reinicios y la pasarela debe deduplicar también. Se gana seguridad pagando almacenamiento y coordinación.

### 2.7 · Plazos y cancelación en una cadena
El primer servicio define un deadline y cada salto propaga el presupuesto restante, reservando tiempo para regresar la respuesta. Los servicios limitan llamadas y tareas, observan cancelación y liberan recursos cuando ya no hay presupuesto. Eso evita trabajar indefinidamente para un cliente que se rindió. La cancelación es cooperativa: no garantiza retirar un mensaje ni deshacer un cobro ya confirmado. Para efectos irreversibles mantengo estado consultable e idempotencia, aunque la solicitud original haya terminado.

### 3.8 · Instancia que muere sin avisar
El registro conserva temporalmente la instancia hasta detectar la falta de heartbeats o vencer su lease. Mientras tanto, clientes con listas antiguas pueden elegirla y fallar. Uso health checks, renovación de listas, timeouts y reintentos limitados solo cuando son seguros; un circuit breaker puede evitar insistir. Reducir tiempos detecta antes, pero produce falsos positivos ante pausas o congestión. La eliminación del registro no implica que todas las cachés de los clientes se hayan actualizado inmediatamente.

### 3.14 · Replicación y partición
Replicar mantiene copias de los mismos datos: puede mejorar disponibilidad y repartir lecturas, pero exige sincronizar y decidir sobre conflictos. Particionar divide los datos entre nodos: reparte almacenamiento y trabajo, pero complica rutas, consultas y transacciones que cruzan particiones. Partir no crea por sí mismo una copia de respaldo. Usaría réplicas para tolerar caídas y particiones cuando la carga o tamaño ya no caben; frecuentemente cada partición tiene sus propias réplicas.

### 4.3 · Linealizabilidad y consistencia eventual
La linealizabilidad hace que cada operación parezca ocurrir en un instante entre su inicio y fin, respetando el orden real de operaciones no solapadas. La consistencia eventual permite ver valores distintos temporalmente y promete convergencia bajo sus condiciones si cesan las actualizaciones. La primera necesita coordinación y puede perder disponibilidad durante una partición. La segunda facilita lecturas disponibles, pero exige tolerar retrasos y resolver conflictos. Para una decisión sobre dinero usaría garantías fuertes; para una vista de catálogo puede bastar eventual.

### 4.8 · Borrado accidental sin respaldos
La replicación suele propagar también el borrado válido pero equivocado: las copias no distinguen la intención humana. Tener réplicas no equivale a conservar una versión histórica recuperable. Frenaría cambios, investigaría WAL o archivos retenidos y copias rezagadas sin prometer recuperación; si no existe historia útil, los datos pueden perderse. Necesito respaldos independientes, retención y pruebas de restauración, idealmente recuperación a un punto en el tiempo. Pago almacenamiento y operación a cambio de poder deshacer errores.

### 5.4 · También falla la compensación
Persisto el estado de la saga como compensación pendiente y reintento el reembolso con la misma identidad de operación, con espera creciente y límites. Consulto al proveedor si el resultado fue ambiguo y registro intentos para reconciliar. Si no converge, alerto y paso a intervención manual; no marco el pedido como totalmente revertido. Compensar es otra operación de negocio que puede fallar, no un rollback mágico. Esta estrategia conserva trazabilidad, aunque el usuario deba ver un estado pendiente durante un tiempo.

### 5.9 · Circuit breaker
El circuit breaker evita consumir recursos insistiendo en una dependencia que está fallando. Cerrado permite llamadas y mide fallas relevantes; al superar el umbral abre y rechaza rápidamente durante una espera. Semiabierto deja pasar pocas pruebas: si funcionan cierra y si fallan vuelve a abrir. Se gana aislamiento y tiempo de recuperación, pero se rechazan solicitudes que quizá habrían funcionado. No sustituye timeouts ni crea una respuesta correcta: una alternativa degradada debe ser válida para el negocio.

### 6.9 · Publicar después del commit y Outbox
Publicar directamente después del commit deja una ventana: la base confirma y el proceso cae antes de avisar al broker. Outbox guarda el cambio y el evento pendiente en la misma transacción local; un relevo publica después y reintenta. Así no pierde la intención de publicar mientras conserve esa base, pero puede enviar duplicados si cae entre publicar y marcar enviado. Lo usaría cuando no puedo perder eventos de negocio; pago una tabla, limpieza, retraso y consumidores idempotentes.

### 6.13 · Consumidor caído y DLQ
Con ack manual, si el consumidor cae antes de confirmar, el broker puede volver a entregar el mensaje; parte del efecto quizá ya ocurrió. Por eso deduplico y coordino el cambio con el registro de procesamiento, y envío el ack después del commit. Los fallos transitorios se reintentan con límites. Una DLQ aparta mensajes que no se pueden procesar para inspección, corrección y reenvío controlado. Evita bloquear el flujo con un mensaje defectuoso, pero necesita monitoreo y no arregla el error por sí sola.

### 7.3 · Medir una duración
Uso un reloj monotónico del mismo proceso para restar fin menos inicio, como System.nanoTime, porque no retrocede por ajustes de hora civil. El reloj de pared sirve para fechas legibles, pero NTP o un cambio manual pueden distorsionar una duración. No resto valores monotónicos tomados en máquinas diferentes: sus orígenes no son comparables. Para una llamada mido ida y vuelta desde el cliente; para repartir el tiempo entre servicios uso trazas y considero la incertidumbre de sus relojes.

### 8.6 · Invalidación de caché
Invalidar es borrar o marcar no utilizable una copia cuando cambia su fuente. Es difícil porque base y caché normalmente no comparten transacción, hay lecturas concurrentes y una caída puede separar commit y borrado. Borrar antes del commit permite que otro lector vuelva a guardar el dato viejo; borrar después reduce esa ventana, pero también puede fallar o competir con lecturas anteriores. Uso TTL, invalidación posterior al commit y, si hace falta, eventos o versiones. Gano velocidad pagando una garantía explícita de frescura.

### 9.4 · Logs, métricas y trazas
Los logs describen eventos concretos y ayudan a responder qué ocurrió y con qué contexto. Las métricas agregan valores en el tiempo y muestran cuánto tráfico, errores, saturación o latencia hay. Las trazas conectan spans de una petición para encontrar dónde pasó el tiempo y en qué dependencia falló. Las correlacionaría con identificadores y alertas, evitando secretos y cardinalidad excesiva. Obtengo diagnóstico del sistema completo a cambio de instrumentación, almacenamiento y muestreo.

## Antes de entregar

- Explicar cada respuesta sin leerla y sustituir expresiones que no sean propias.
- Escribir las 25 respuestas a mano con sus números, de forma legible.
- Escanear todas las páginas a un único PDF y revisar orientación y cortes.
- Guardar en `entregas/santana_derek/examen/examen-escrito.pdf` y hacer push.
- El borrador no sustituye ese archivo ni la entrevista individual del 21 de noviembre.
