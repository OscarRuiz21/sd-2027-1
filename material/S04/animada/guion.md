# Cómo se hablan los servicios

Cada línea numerada es un paso (un clic) de la presentación; lo que va en **negritas** se resalta en pantalla.

Controles: → o clic avanza · ← regresa · N guion y notas · P reproduce solo · F pantalla completa.

---

## 00 / REST · REPASO — Recursos y verbos. Lo que ya vimos en la S3.

1. REST nombra **recursos** (cuentas, pedidos) y los mueve con verbos.
2. La respuesta trae el estado del recurso y, sobre todo, **un código**.
3. Dos equis funcionó, cuatro equis es tu error, cinco equis **es el nuestro**.
4. Y lo de la S3: repetir la misma petición **no debe cobrar dos veces**.

**Para explicar**

- Arranque de la sección B. Enlaza con la S3: timeout, at-least-once, idempotencia. RFC 9110 para semántica de verbos y códigos.
- La idea que se quiere dejar: REST es la red hecha explícita. El contrato vive en la documentación, no en el código.

---

## 01 / REST · LAS SEIS BASES — Seis bases. Y una de pilón.

1. Uno: **recursos**. Sustantivos con dirección propia: /usuarios, /pedidos.
2. Dos: **representaciones**. Casi siempre JSON, pero el recurso no es el JSON.
3. Tres: **métodos predecibles**. La acción va en el verbo, nunca en la URL.
4. Cuatro: **sin estado**. Cada petición trae lo que necesita para entenderse sola.
5. Cinco: **interfaz uniforme**. Las mismas reglas para todos los recursos.
6. Seis: **cliente y servidor aparte**. El cliente no sabe cómo guardas los datos.
7. Y de pilón: **respuestas cacheables**. Quien va en medio puede guardar la copia.
8. Ahora la pregunta del grupo: ¿por qué esto **aguanta** un sistema distribuido?

**Para explicar**

- Las seis bases en el orden en que conviene enseñarlas: recursos, representaciones, métodos, sin estado, interfaz uniforme y separación cliente-servidor. El extra es cacheabilidad. Fielding, cap. 5 (2000): son restricciones, no recomendaciones.
- Respuestas para la discusión: **sin estado** = cualquier réplica atiende cualquier petición, así que escalar es agregar instancias (enlaza con el factor VI y con el balanceador). **Cacheable** = el tráfico no llega al origen. **Interfaz uniforme** = los intermediarios (proxies, gateways, CDNs) operan sin saber del dominio. **Cliente y servidor aparte** = las dos partes evolucionan por separado, que es justo lo que un sistema distribuido necesita para no romperse en cada despliegue.
- Contraste que deja abierto el paso a gRPC: todo esto se paga con texto en el cable y contrato opcional. Preguntar: ¿qué de estas seis bases conserva gRPC?

---

## 02 / REST · A FONDO — El contrato opcional. Lo que igual hay que resolver.

1. REST se ve simple, pero el cliente pide más: paginar, versionar, **aguantar límites**.
2. Y la red obliga al resto: timeouts, reintentos con espera y **idempotencia**.
3. El contrato existe, pero es **opcional**: se escribe aparte… o no se escribe.
4. Funciona muy bien en la frontera pública. Adentro, **pide algo más**.

**Para explicar**

- Los seis temas salen del mapa de 12 conceptos de API: REST, idempotencia, paginación, rate limits, versionado, webhooks, gRPC, GraphQL, auth, reintentos, timeouts y códigos de estado.
- Puente hacia gRPC: en la frontera pública el costo en bytes se paga una vez; entre servicios, mil veces por segundo.

---

## 03 / EL PUENTE — Una acción. Muchas llamadas.

1. Un usuario pide un viaje. Para él es **una acción**.
2. Adentro, viajes llama a conductores, precios, ubicación y notificaciones.
3. Cuatro llamadas por una. Y en hora pico, **miles por segundo**.
4. Cada flecha abre conexión, saluda con TCP y TLS, y **espera su turno**.

**Para explicar**

- Ejemplo tomado del video: backend de una app de viajes. Sirve para justificar HTTP/2 antes de nombrar gRPC.
- Dos costos: el handshake de cada conexión nueva y la fila dentro de una misma conexión (head-of-line blocking en HTTP/1.1).

---

## 04 / HTTP/2 — Una conexión. Muchos streams.

1. Antes, cada llamada abría **su propia conexión**: TCP, TLS y a empezar de nuevo.
2. Y si compartían una, las peticiones **hacían fila**, una detrás de otra.
3. HTTP/2 deja **una conexión abierta** entre los dos servicios.
4. Adentro, cada petición viaja en **su propio stream**, al mismo tiempo.
5. Y lo que se repite (auth, trazas, metadatos) **se comprime** en vez de reenviarse entero.

**Para explicar**

- Orden del video: primero qué da HTTP/2, después qué agrega gRPC encima. gRPC no reemplaza a HTTP: corre sobre él.
- Multiplexación: varios streams en una conexión. HPACK comprime encabezados. En HTTP/1.1 la fila es a nivel de petición; en HTTP/2 queda a nivel de TCP.

---

## 05 / PROTOBUF — Los mismos datos. Menos bytes.

1. JSON es texto y se lee de corrido, pero carga **el nombre de cada campo**.
2. Protobuf es binario: número de campo y valor. **Sesenta bytes** en vez de ciento ochenta.
3. Una llamada no se nota. **Mil por segundo**, sí.
4. No se perdió nada: el nombre lo pone el contrato, y el contrato **ya lo tienen los dos**.

**Para explicar**

- Números del reel de gRPC vs REST: mismo dato, 180 B con REST contra 60 B con gRPC. El ejemplo 38 → 9 bytes del deck actual es el mismo argumento en pequeño.
- Protobuf codifica número de campo + tipo (wire type) + valor. Los nombres solo existen en el .proto.

---

## 06 / EL CONTRATO — Un archivo. Y el código se genera.

1. Los dos lados acuerdan **un archivo**: servicios, métodos y mensajes.
2. De ahí, protoc **genera el código** de cliente y servidor.
3. Cada quien en su lenguaje: Go llamando a Python, Java llamando a Node.
4. Y en el código se ve así: **una función normal**.
5. Debajo, gRPC serializa, manda, espera y devuelve. Eso es **remote procedure call**.

**Para explicar**

- Del diálogo: "un contrato, muchos lenguajes". El .proto es contrato obligatorio; en REST, OpenAPI es opcional.
- Ojo con la lección de 1984 (Birrell y Nelson) y la Nota sobre computación distribuida (Waldo, 1994): la llamada parece local, pero la red sigue ahí. Por eso gRPC trae deadline en cada llamada.

---

## 07 / LOS CUATRO MODOS — No siempre. Es pregunta y respuesta.

1. El unario es el clásico: **una petición, una respuesta**.
2. Con streaming del servidor, pides una vez y **te van llegando** respuestas.
3. Con streaming del cliente, tú mandas muchas y **él contesta al final**.
4. Y bidireccional: los dos hablan **al mismo tiempo**, por el mismo stream.
5. Esto es lo que HTTP/1.1 no daba, y por eso **venía con REST limitado**.

**Para explicar**

- Del diálogo: "HTTP/2 permite cuatro modos: unario, streaming del servidor, del cliente y bidireccional".
- Aterrizarlo en el sistema del curso: transferencias → cuentas es unario; la telemetría hacia observabilidad sería streaming.

---

## 08 / COMPROBACIÓN — ¿Cuál es cuál? Cuatro escenarios, cuatro modos.

1. Cuatro escenarios. Digan en el chat **qué modo** usarían en cada uno.
2. A, el saldo: una pregunta, una respuesta. Es **unario**.
3. B, la ubicación: pides una vez y **te van llegando**. Streaming del servidor.
4. C, los fragmentos: mandas muchos y llega **un resultado**. Streaming del cliente.
5. D, el chat: los dos hablan cuando quieren. **Bidireccional**.
6. La regla para decidir: cuenta **cuántos mensajes** manda cada lado.

**Para explicar**

- Cómo correrla: mostrar los cuatro escenarios, un minuto de votación en el chat y revelar uno por uno. Si alguien duda entre B y D, la pregunta que desempata es si el otro lado también manda mensajes.
- Aterrizaje en el sistema del curso: transferencias → cuentas es unario; la telemetría hacia observabilidad sería streaming del servidor; una carga de archivo por partes al almacén de objetos, streaming del cliente; el chat de soporte, bidireccional.
- Ojo con el reflejo de usar streaming para todo: el unario cubre el noventa por ciento de las llamadas y es el más fácil de reintentar y de depurar.

---

## 09 / EL TRADE-OFF — REST o gRPC. Mismo problema, precios distintos.

1. Las mismas siete filas, leídas como **precios** y no como méritos.
2. La frontera pública favorece REST: **cualquier cliente lo lee** y se depura con curl.
3. El tráfico interno favorece gRPC: miles de llamadas, y **el contrato evita divergencias**.
4. Y hay un límite duro: **el navegador no habla gRPC** nativo; gRPC-Web tiende el puente.
5. En nuestro sistema: el gateway habla REST; transferencias a cuentas **es el candidato**.

**Para explicar**

- Válvula del deck: si a las 08:16 se va tarde, esta escena se cuenta en dos minutos con la pura tabla, sin los recuadros.
- Del video: para APIs públicas que consume el navegador, HTTP normal es más simple. gRPC brilla entre servicios de backend con rendimiento, contratos fuertes, streaming y varios lenguajes.

---

## 10 / LAS SEIS PIEZAS — Partir el monolito. Crea seis preguntas.

1. Ayer era un proceso: las llamadas eran **funciones en memoria**.
2. Hoy son tres servicios, cada uno **dueño de su base**.
3. Y cada flecha entre ellos **ya cruza la red**: puede tardar, fallar o repetirse.
4. Eso abre seis preguntas que el monolito nunca tuvo que contestar.
5. Cada pieza de hoy existe porque **responde una de esas seis**.

**Para explicar**

- Es el mapa del deck (slide 7). Las seis preguntas: entrada, descubrimiento, configuración, acoplamiento de disponibilidad, lecturas repetidas y visibilidad.
- Insistir: ninguna pieza se agrega por moda; cada una paga una pregunta concreta.

---

## 11 / PIEZAS 1 A 3 — Entrar, encontrar. Y saber con qué valores.

1. Sin gateway, cada servicio expone su puerto y **el cliente se acopla** a la topología.
2. El gateway pone una sola puerta: TLS, identidad y límites **se programan una vez**.
3. En contenedores la dirección cambia en cada arranque: el **nombre** se separa de la IP.
4. Y la misma imagen corre en dev y en prod: la configuración **va por fuera**.
5. Tres piezas, tres preguntas: por dónde entro, a quién llamo, **con qué valores corro**.

**Para explicar**

- Datos duros del deck: Spring Cloud Gateway 5 (nov-2025); Gateway API GA desde el 31-oct-2023. Eureka late cada 30 s y expulsa a los 90 s; la JVM cachea DNS 30 s. Factor III: config es todo lo que varía entre despliegues.
- Incidentes: Cloudflare 18-nov-2025 (el proxy central devolvió 5xx de 11:28 a 17:06 UTC). Roblox 28–31-oct-2021 (Consul degenerado, 73 horas). Fastly 8-jun-2021 (un cambio de configuración válido, 85 % de la red con errores en 49 minutos).

---

## 12 / PIEZAS 4 A 6 — Aguantar, no repetir. Y poder contar la historia.

1. Llamada directa: si notificaciones está caído, **la transferencia falla con él**.
2. El broker guarda el mensaje: **entrega al menos una vez**, cuando el otro vuelva.
3. La base es el recurso caro y compartido, y las mismas lecturas **se repiten mil veces**.
4. La caché es una copia que se permite estar vieja: **si está, hit**; si no, miss y TTL.
5. Y cuando la operación cruza cuatro servicios, sin **trace id** hay veinte bitácoras y ninguna historia.
6. Tres piezas más: aguantar que el otro falle, no repetir trabajo y **ver el recorrido**.

**Para explicar**

- Datos duros: at-least-once con ack tras persistir y dead-letter queue; RabbitMQ 4.0 (sep-2024), Kafka 4.0 sin ZooKeeper (mar-2025). De 99 % a 98 % de aciertos se duplica el tráfico a la base. W3C traceparent: versión, trace id, parent id, flags; OpenTelemetry con las tres señales estables.
- Incidentes: AWS Kinesis 25-nov-2020 (Cognito escribía al broker de forma bloqueante y el servicio "asíncrono" cayó también). Slack 22-feb-2022 (Memcached vacío en el pico; la base hizo timeout y la caché no se podía rellenar).

---

## 13 / CIERRE — Cuatro factores. Ya los vieron hoy.

1. La configuración fuera de la imagen: eso es **el factor tres**.
2. Cualquier instancia atiende porque **el estado no vive en ella**: factor seis.
3. Broker, caché y base son adjuntos por URL: **factor cuatro**.
4. Y los logs son flujos que alguien más junta: **factor once**.
5. Seis piezas, seis preguntas, y cuatro factores que ya **quedaron ejercidos**.
6. Lo que sigue el sábado: el sistema corriendo, y **la decisión de FarmaYa**.

**Para explicar**

- 12factor.net: Wiggins, Heroku, 2011. Los otros ocho factores salen en el lab de hoy y en la S5.
- Válvula del deck: esta escena se puede contar en una frase si el reloj aprieta. La decisión de FarmaYa a las 09:44 nunca se recorta.

---

## 14 / EL MAPA COMPLETO — Todas juntas. Seis piezas, un sistema.

1. El mapa completo: los clientes entran **por una sola puerta**.
2. Detrás, tres servicios, y cada flecha entre ellos **cruza la red**.
3. Cada uno **dueño de su base**: nadie entra a la del vecino.
4. Y las transversales, que hablan con todos: caché, discovery, config, broker y trazas.
5. Seis piezas, seis preguntas, **una línea cada una**.
6. Quita cualquiera y la pregunta que resolvía **vuelve a aparecer**.

**Para explicar**

- Es la lámina de recapitulación del deck (slide 17). Sirve para cerrar la sección C y para volver a ella en la S5, cuando el sistema corra con compose.
- Si el reloj aprieta, esta escena se cuenta en treinta segundos: señalar la puerta, los tres dueños de su base y la banda de transversales.

---

## 15 / LA TAREA — El mismo servicio. Dos veces.

1. La tarea: **el mismo servicio**, implementado dos veces. REST y gRPC.
2. Las dos dockerizadas, y un compose que las levante **juntas**.
3. Va por push, como siempre, **antes del sábado que entra**.
4. No se evalúa que sea grande: se evalúa que puedan **comparar** las dos.

**Para explicar**

- Sugerencia de caso de uso, para que la comparación sea justa: un único método, por ejemplo consultar el saldo de una cuenta o buscar conductores cercanos. Lo interesante no es el tamaño, sino las dos formas de exponerlo.
- Qué mirar al calificar: que el .proto exista y sea el contrato real (no documentación aparte), que el compose levante ambos servicios, y que la tabla de comparación tenga números medidos, no copiados de una lámina.
- La fecha propuesta es el sábado 19 antes de las 07:00, para que llegue antes de la S5. Ajustarla si se prefiere el domingo 20, como en los labs.
- La lectura de la semana queda pendiente de definir; no se anuncia en pantalla. Candidatas: Burns y Oppenheimer, patrones de contenedores (HotCloud 2016), que acompaña al lab de compose; ZooKeeper (USENIX ATC 2010), que explica por dentro discovery y config; o Raft (2014) si se abre consenso.
