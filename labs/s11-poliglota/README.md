# Lab S11 · Dos bases más y una caché

**Sistemas Distribuidos 2027-1 · Grupo 2 · S11 · sábado 31 de octubre**
**Entrega: sábado 7 de noviembre de 2026, antes de las 07:00** (antes de la clase de la S12)

## Objetivo

Al terminar, Mexi Banco usa **tres almacenes** y cada uno hace lo que mejor sabe: PostgreSQL sigue
guardando la verdad del dinero, **MongoDB** guarda la bitácora de avisos y **Redis** guarda una copia
del saldo para mostrarlo rápido. Y puedes decir, con números tuyos, qué ganaste y qué riesgo
aceptaste con cada uno.

## Contexto

Hoy vimos que no hay una base que sirva para todo. La pregunta no es "SQL o NoSQL", sino **qué dato
es la verdad y qué dato es derivado**. La verdad tiene que estar en un lugar que no la pierda ni la
contradiga. Lo derivado se puede reconstruir, y por eso se vale guardarlo en otro motor, con otra
forma y hasta un poco atrasado.

En Mexi Banco eso queda así:

| Dato | Dónde vive | ¿Verdad o derivado? | Cómo se mantiene |
|---|---|---|---|
| Cuenta y saldo (`cuenta`) | PostgreSQL | Verdad | Transacción local con `@Version` |
| Movimientos (`movimiento`) | PostgreSQL | Verdad | Asientos con Idempotency-Key (S08) |
| Transferencias y outbox (`transferencia`) | PostgreSQL | Verdad | Saga y Outbox (S08 y S09) |
| Saldo **para mostrar** | **Redis** | Derivado, puede ir hasta 30 s atrás | DEL al cambiar el saldo, más TTL |
| Avisos enviados (`notificacion`) | **MongoDB** | Verdad de la bitácora de avisos | Consume `TransferenciaRealizada` (S09) |

Nada de lo que es dinero cambia de motor. Lo que cambia es lo que está alrededor:

- **Los avisos** tienen una forma distinta según el canal: un SMS lleva teléfono, un correo lleva
  dirección y asunto, un push lleva el token del celular. Se escriben una vez, no se actualizan y se
  leen enteros por CLABE. Es un caso de documentos.
- **El saldo que el cliente consulta** se pide muchas más veces de las que cambia. Es un caso de
  caché: una copia que se permite estar vieja un rato, siempre que **nunca** se use para decidir.

## Antes de empezar

El punto de partida es la **solución del lab S09** más Redis y MongoDB ya instalados, en la rama
`06-poliglota-base`. Construye las imágenes **antes de clase**: bajan dependencias nuevas y la primera
vez tardan varios minutos.

```bash
# Baja el repo ya en la rama de este lab, en una carpeta nueva
git clone --branch 06-poliglota-base https://github.com/OscarRuiz21/mexi-banco.git mexi-banco-s11
# Entra a la carpeta
cd mexi-banco-s11
# Construye las imágenes y levanta todo en segundo plano
docker compose up --build -d
# Muestra el estado de cada contenedor; espera a que todos digan healthy
docker compose ps
```

Si ya tienes el repo: `git fetch origin` (trae las ramas nuevas) y `git switch 06-poliglota-base`
(cámbiate a la del lab).

- Si tu laptop sufre, baja la carga: `docker compose up -d --scale cuenta=1` (una sola copia de
  cuenta) y `docker compose stop spei` (este lab no usa spei). Para la Parte 1 conviene dejar las
  tres copias de cuenta: así ves que la caché es compartida.
- Si el puerto 8080 está ocupado: `PUERTO_GATEWAY=18080 docker compose up -d`.
- Redis queda en `localhost:6379` y MongoDB en `localhost:27017`, solo en tu máquina. Si quieres una
  interfaz gráfica, MongoDB Compass y RedisInsight se conectan ahí. No hacen falta: todo el lab se
  puede hacer con `mongosh` y `redis-cli` dentro de los contenedores.

**Cómo sabes que la base corre:**

```bash
# Le pregunta a Redis si está vivo; debe contestar PONG
docker compose exec redis redis-cli ping
# Abre la consola de MongoDB en la base notificacion y cuenta los avisos; debe dar 0
docker compose exec mongo mongosh notificacion --eval 'db.avisos.countDocuments()'
```

Y una transferencia por el gateway responde `201` con estado `COMPLETADA`, igual que en la S09.

### Datos para todo el lab

Abre tres cuentas. Cada comando crea una cuenta con su saldo inicial:

```bash
# Crea a Ana con 1000
curl -X POST localhost:8080/api/cuentas -H 'Content-Type: application/json' -d '{"clabe":"002180000000000001","titular":"Ana","saldoInicial":1000}'
# Crea a Beto con 500
curl -X POST localhost:8080/api/cuentas -H 'Content-Type: application/json' -d '{"clabe":"002180000000000002","titular":"Beto","saldoInicial":500}'
# Crea a Carla con 300
curl -X POST localhost:8080/api/cuentas -H 'Content-Type: application/json' -d '{"clabe":"002180000000000003","titular":"Carla","saldoInicial":300}'
```

Y para transferir:

```bash
# Manda 200 de Ana a Beto
curl -X POST localhost:8080/api/transferencias -H 'Content-Type: application/json' -d '{"claveOrigen":"002180000000000001","claveDestino":"002180000000000002","monto":200}'
```

## El contrato

Usa **exactamente** estos nombres. Así se revisa tu entrega y así tu `mongosh` y tu `redis-cli` dan
lo mismo que los de tus compañeros.

**Caché (en `cuenta`):**

| Pieza | Valor |
|---|---|
| Nombre de la caché | `cuentas` (constante `ConfiguracionCache.CUENTAS`) |
| Clave en Redis | `cuentas::<clabe>`, por ejemplo `cuentas::002180000000000001` |
| Valor | JSON de `CuentaVista`: `clabe`, `titular`, `saldo`, `leidaEn` |
| TTL | 30 s (`mexibanco.cache.ttl`, variable `CACHE_TTL`) |
| Se llena | En `GET /cuentas/{clabe}`, con `@Cacheable` en `ConsultaDeSaldo` |
| Se borra | Al aplicar un cargo o un abono, con `@CacheEvict`, **después del commit** |

**Bitácora de avisos (en `notificacion`):**

| Pieza | Valor |
|---|---|
| Base y colección | Base `notificacion`, colección `avisos` |
| `_id` | `<eventoId>:<clabe>`, por ejemplo `8a1f2c3e-...:002180000000000002` |
| Campos de todos | `eventoId`, `transferenciaId`, `clabe`, `tipo`, `monto` y `saldo` (Decimal128), `fecha` (la del evento), `canal` |
| Canal `SMS` | `telefono`, `texto` |
| Canal `CORREO` | `correo`, `asunto`, `cuerpo` |
| Canal `PUSH` | `token`, `titulo`, `cuerpo` |
| Índice | `clabe_fecha`: `{ clabe: 1, fecha: -1 }` |
| Endpoint | `GET /api/notificaciones/{clabe}`: los últimos 10, del más reciente al más viejo |

¿Quién recibe qué canal? Ya está resuelto en `DirectorioDeContactos`: Ana (`...001`) pidió SMS,
Beto (`...002`) pidió correo y cualquier otra cuenta, como Carla, recibe un push.

¿Por qué el `_id` no es solo el `eventoId`? Porque una `TransferenciaRealizada` genera **dos** avisos,
uno para quien envía y otro para quien recibe. El mismo evento con la misma CLABE da siempre el mismo
`_id`; ese es el truco de la Parte 3.

## Parte 0 · Ver el punto de partida (≈5 min)

1. Consulta el saldo de Ana diez veces:

   ```bash
   # Pide el saldo de Ana diez veces seguidas
   for i in $(seq 1 10); do curl -s localhost:8080/api/cuentas/002180000000000001; echo; done
   ```

2. Cuenta cuántas de esas consultas llegaron a Postgres:

   ```bash
   # Busca en el log de las tres copias de cuenta la línea que sale cada vez que se lee Postgres
   docker compose logs cuenta | grep -c "leida de Postgres"
   ```

3. Mira que Redis no se ha usado y que Mongo está vacío:

   ```bash
   # Muestra cuántas lecturas acertó y falló Redis desde que arrancó
   docker compose exec redis redis-cli INFO stats | grep keyspace
   # Cuenta los avisos guardados en Mongo
   docker compose exec mongo mongosh notificacion --eval 'db.avisos.countDocuments()'
   ```

Anota en la bitácora: cada consulta del saldo es una lectura a Postgres, y los avisos de la S09
siguen en la bandeja de Postgres (`GET /api/notificaciones?clabe=...`).

## Parte 1 · La caché del saldo (≈25 min, guiada)

Busca los comentarios `// TODO S11:` en `cuenta`. Son tres archivos:

1. **`ConfiguracionCache`**: ponle `@EnableCaching` y agrega el bean que configura la caché
   `cuentas`. Spring Boot ya arma el `RedisCacheManager`; tú solo le cambias tres cosas:

   ```java
   @Bean
   RedisCacheManagerBuilderCustomizer cacheDeCuentas(@Value("${mexibanco.cache.ttl:30s}") Duration ttl, ObjectMapper json) {
       RedisCacheConfiguration cuentas = RedisCacheConfiguration.defaultCacheConfig()
           .entryTtl(ttl)                       // cada entrada vive 30 s
           .disableCachingNullValues()
           .serializeValuesWith(SerializationPair.fromSerializer(
               new Jackson2JsonRedisSerializer<>(json, CuentaVista.class)));   // JSON legible
       return builder -> builder.withCacheConfiguration(CUENTAS, cuentas)
           .transactionAware();                 // put y evict esperan al commit
   }
   ```

2. **`ConsultaDeSaldo.consultar`**: `@Cacheable(cacheNames = ConfiguracionCache.CUENTAS, key = "#clabe")`.
   Esta es la lectura para **mostrar**. Es cache-aside: en un hit contesta Redis y el método ni
   corre; en un miss corre, lee Postgres y Spring guarda el resultado.

3. **`CuentaService.cargar` y `abonar`**: `@CacheEvict(cacheNames = ConfiguracionCache.CUENTAS, key = "#clabe")`.
   El saldo cambió, así que se **borra** la copia. No se actualiza: se borra.

Ojo con lo que **no** se toca: `CuentaService.consultar`. Es la lectura que **autoriza** el cargo y
siempre va a Postgres. Por eso la consulta cacheable vive en otra clase. Además, si pusieras
`@Cacheable` en un método que se llama desde la misma clase, Spring lo ignoraría sin avisar.

Reconstruye solo cuenta:

```bash
# Vuelve a construir la imagen de cuenta y reemplaza sus tres copias
docker compose up --build -d cuenta
```

**Terminado cuando** puedas mostrar esto:

```bash
# Pide el saldo de Ana seis veces y muestra qué copia de cuenta contestó cada vez
for i in 1 2 3 4 5 6; do curl -s -D - localhost:8080/api/cuentas/002180000000000001 | grep -iE 'x-instancia|leidaEn'; done
# Cuenta cuántas veces se leyó Postgres en los últimos 30 segundos
docker compose logs --since 30s cuenta | grep -c "leida de Postgres"
# Muestra la copia guardada en Redis
docker compose exec redis redis-cli GET cuentas::002180000000000001
# Muestra cuántos segundos le quedan de vida
docker compose exec redis redis-cli TTL cuentas::002180000000000001
```

1. `X-Instancia` cambia entre las tres copias de cuenta, pero Postgres se leyó **una** vez y
   `leidaEn` es el mismo en las seis respuestas: la caché la comparten las tres.
2. En Redis está el JSON y el TTL es de 30 o menos.
3. Haces una transferencia de Ana a Beto y, justo después,
   `docker compose exec redis redis-cli EXISTS cuentas::002180000000000001` da `0`: la copia se
   borró. El siguiente GET trae el saldo nuevo.

Para ver el orden de las operaciones, deja corriendo en otra terminal
`docker compose exec redis redis-cli MONITOR` (muestra cada comando que recibe Redis) durante una
transferencia. Verás los `GET`, el `SET ... PX 30000` de un miss y los `DEL` al final.

### Mide (para la bitácora)

**Hit ratio.** Redis cuenta sus aciertos (`keyspace_hits`) y sus fallos (`keyspace_misses`). El hit
ratio es `hits / (hits + misses)`. Mídelo en dos escenarios:

```bash
# Pone en cero los contadores de Redis
docker compose exec redis redis-cli CONFIG RESETSTAT
# A) Cien consultas seguidas del saldo de Ana
for i in $(seq 1 100); do curl -s -o /dev/null localhost:8080/api/cuentas/002180000000000001; done
# Muestra los contadores
docker compose exec redis redis-cli INFO stats | grep keyspace
```

Escenario B: lo mismo, pero con una transferencia de 1 peso de Ana a Beto cada diez consultas.
Explica por qué baja el hit ratio. Ojo: tus propios `redis-cli GET` también cuentan.

**Latencia.** Mide cien consultas con y sin caché. Este comando hace 100 GET y te da el promedio, la
mediana y el p95 en milisegundos:

```bash
# Hace 100 consultas, guarda el tiempo de cada una, las ordena y calcula promedio, mediana y p95
for i in $(seq 1 100); do curl -s -o /dev/null -w '%{time_total}\n' localhost:8080/api/cuentas/002180000000000001; done | sort -n | awk '{t[NR]=$1*1000; s+=$1*1000} END {printf "promedio=%.2f ms mediana=%.2f ms p95=%.2f ms\n", s/NR, t[int(NR*0.5)], t[int(NR*0.95)]}'
```

Para medir sin caché no hace falta tocar código:

```bash
# Reinicia cuenta con la caché apagada (spring.cache.type=none); espera a que esté healthy
CACHE_TIPO=none docker compose up -d cuenta
# ... mide igual ...
# Regresa cuenta con la caché encendida
docker compose up -d cuenta
```

Corre cada medición dos o tres veces y reporta todas. No te sorprendas si la diferencia es chica: en
tu laptop Postgres tiene la tabla en memoria y está a un salto de red. Cuenta también cuántas veces
se leyó Postgres en cada caso (el `grep -c "leida de Postgres"` de arriba). Esa es la otra mitad de
la respuesta.

## Parte 2 · Los avisos en MongoDB (≈25 min)

En `notificacion`, busca `// TODO S11:` en `avisos/BitacoraDeAvisos.java` y en
`mensajeria/ConsumidorDeTransferencias.java`:

1. **`documento(...)`**: arma el aviso como `org.bson.Document`, con el `_id` y los campos comunes del
   contrato, y luego un `switch` sobre el `Contacto` que agregue los campos de cada canal. `Contacto`
   es una interfaz sellada: si olvidas un canal, no compila. Usa `new Decimal128(...)` para el
   dinero (nunca `double`) y `Date.from(evento.fecha())` para la fecha.
2. **`registrar(evento)`**: por cada destinatario, `mongo.insert(documento, COLECCION)`. Inserta uno
   por uno y cuenta los nuevos. Si salta `DuplicateKeyException`, ese aviso ya estaba: no es error,
   no se cuenta y se sigue con el siguiente.
3. **`crearIndice()`**: `mongo.indexOps(COLECCION).ensureIndex(...)` con `clabe` ascendente y
   `fecha` descendente, llamado `clabe_fecha`.
4. **`ultimos(clabe)`**: una `Query` con `clabe` igual, ordenada por `fecha` descendente y con
   `limit(ULTIMOS)`, leída como `Document.class`.
5. **El consumidor**: inyecta `BitacoraDeAvisos` y guarda con `bitacora.registrar(evento)` en lugar
   de la bandeja de Postgres. **No escribas en los dos**: sería una doble escritura sin transacción
   común, el problema de la S09.

Reconstruye solo notificación:

```bash
# Vuelve a construir la imagen de notificacion y la reemplaza
docker compose up --build -d notificacion
```

**Terminado cuando:**

1. Haces una transferencia de Ana a Beto y otra de Ana a Carla, y en Mongo ves las tres formas:

   ```bash
   # Muestra todos los avisos, del más viejo al más nuevo
   docker compose exec mongo mongosh notificacion --eval 'db.avisos.find().sort({fecha: 1})'
   # Muestra solo los avisos que tienen teléfono: el esquema se aplica al leer
   docker compose exec mongo mongosh notificacion --eval 'db.avisos.find({telefono: {$exists: true}}, {clabe: 1, canal: 1, telefono: 1})'
   ```

2. El endpoint da los últimos 10 de Beto, del más nuevo al más viejo. Haz doce transferencias
   pequeñas de Ana a Beto para comprobar el límite:

   ```bash
   # Pide la bitácora de avisos de Beto por el gateway
   curl -s localhost:8080/api/notificaciones/002180000000000002
   ```

3. El índice existe y la consulta lo usa:

   ```bash
   # Lista los índices de la colección; debe aparecer clabe_fecha
   docker compose exec mongo mongosh notificacion --eval 'db.avisos.getIndexes()'
   # Pide el plan de la consulta de ultimos(); busca IXSCAN y cuántos documentos examinó
   docker compose exec mongo mongosh notificacion --eval 'db.avisos.find({clabe: "002180000000000002"}).sort({fecha: -1}).limit(10).explain("executionStats")'
   ```

   Repite el `explain` agregando `.hint({$natural: 1})` antes de `.explain(...)`, que obliga a
   Mongo a no usar el índice. Compara `totalDocsExamined` y la etapa (`COLLSCAN` y `SORT`).

## Parte 3 · Rompe algo: el evento duplicado (≈5 min, o de tarea)

La mensajería de la S09 entrega **al menos una vez**. Publica el mismo evento dos veces desde la API
del panel de RabbitMQ. Usa CLABEs inventadas para no mezclarlo con tus cuentas:

```bash
# Publica en el exchange transferencias un evento fijo; córrelo DOS veces
curl -u mexibanco:mexibanco -X POST localhost:15672/api/exchanges/%2F/transferencias/publish -H 'Content-Type: application/json' -d '{"properties":{"delivery_mode":2,"content_type":"application/json"},"routing_key":"transferencia.realizada","payload_encoding":"string","payload":"{\"eventoId\":\"11111111-2222-4333-8444-555555555555\",\"tipo\":\"TransferenciaRealizada\",\"transferenciaId\":9001,\"clabeOrigen\":\"002180000000000091\",\"clabeDestino\":\"002180000000000092\",\"monto\":25,\"saldoOrigen\":475,\"saldoDestino\":525,\"fecha\":\"2026-10-31T13:00:00Z\"}"}'
# Cuenta los avisos de ese evento; deben ser 2, no 4
docker compose exec mongo mongosh notificacion --eval 'db.avisos.countDocuments({eventoId: "11111111-2222-4333-8444-555555555555"})'
# Busca en el log de notificacion la línea del repetido
docker compose logs notificacion | grep "ya estaba"
```

También lo puedes provocar como en la S09: `PAUSA_ANTES_DEL_ACK_MS=15000 docker compose up -d notificacion`,
una transferencia, `docker compose kill notificacion` durante la pausa y `docker compose up -d notificacion`
para levantarlo otra vez sin pausa. Al volver, el mensaje llega con `redelivered=true` y no se
duplica nada.

**Terminado cuando:** el mismo evento dos veces deja **dos** avisos, y el log dice que el segundo
intento ya estaba.

## Parte 4 · Rompe algo: el dato viejo (≈10 min, o de tarea)

La caché puede mentir. Provócalo cambiando Postgres **por fuera** de cuenta, sin pasar por el
`@CacheEvict`:

```bash
# 1. Consulta a Ana: ahora su saldo queda en la caché
curl -s localhost:8080/api/cuentas/002180000000000001
# 2. Cambia el saldo directo en Postgres, como haría un script mal hecho
docker compose exec db psql -U mexibanco -d cuenta -c "update cuenta set saldo = 5 where clabe = '002180000000000001'"
# 3. Consulta cada 5 segundos: muestra el TTL que le queda y el saldo que ve el cliente
for i in $(seq 1 8); do docker compose exec redis redis-cli TTL cuentas::002180000000000001; curl -s localhost:8080/api/cuentas/002180000000000001; echo; sleep 5; done
```

En otra terminal, **mientras el saldo viejo sigue en la caché**, intenta transferir 100 de Ana a
Beto. Anota el código de respuesta y el mensaje.

Al final, regresa el saldo que tenía Ana (paso 2 con su saldo real). Este `UPDATE` rompe a propósito
el cuadre entre el saldo y los movimientos.

**Terminado cuando** tengas:

1. cuántos segundos el cliente vio un saldo que ya no existía, y cómo lo cuenta `leidaEn`;
2. qué contestó la transferencia mientras la caché decía otra cosa, y por qué.

## Reto (opcional, suma puntos) · Redis se cae

Apaga Redis con `docker compose stop redis` y prueba un GET del saldo y una transferencia. En tu
versión, ¿qué pasa? La caché era solo una copia para mostrar: ¿debería poder tumbar algo?

Haz que cuenta siga funcionando sin Redis: el GET va a Postgres (más lento) y la transferencia se
completa. Antes de elegir dónde atrapar el error, piensa **cuándo** ocurre el `DEL` con
`transactionAware()`, y qué le pasaría a la saga si cuenta contesta 500 a un cargo que **ya se
confirmó**. Un `CacheErrorHandler` parece la respuesta; revisa si de verdad alcanza.

**Terminado cuando:** con Redis apagado, el GET responde 200, la transferencia responde 201, los
saldos cuadran y el log dice qué operación de caché falló. Al encender Redis, todo sigue.

## Cómo se trabaja

Con tu **equipo del proyecto**: se apoyan, se reparten la investigación y se ayudan a depurar.
Pero **cada quien entrega lo suyo**: su código y su bitácora.

## Qué entregas

En tu rama `entregas_apellido_nombre` del repo del grupo:

```
entregas/apellido_nombre/s11/
├── bitacora-s11.pdf
└── codigo/
    ├── cuenta/               ← la carpeta completa del servicio, SIN target/
    ├── notificacion/         ← la carpeta completa del servicio, SIN target/
    └── docker-compose.yml    ← solo si lo cambiaste
```

**La bitácora**, en PDF y con capturas en cada parte:

1. **El punto de partida** (Parte 0): diez consultas, diez lecturas a Postgres.
2. **La caché**: tu código explicado con tus palabras, la evidencia de que las tres copias de cuenta
   comparten la caché, el JSON y el TTL en Redis, y la copia borrada después de una transferencia.
3. **Tus medidas**: hit ratio en los escenarios A y B, latencia con y sin caché (todas las corridas)
   y lecturas a Postgres en cada caso.
4. **Los avisos**: las tres formas en `mongosh`, el endpoint con 10, el índice y los dos `explain`.
5. **El duplicado** (Parte 3) y **el dato viejo** (Parte 4).
6. **Preguntas de cierre** (abajo).
7. **Redis caído**, si hiciste el reto.

```bash
# Cámbiate a tu rama de entregas
git checkout entregas_apellido_nombre
# Trae lo último del repo del grupo
git pull origin main
# Crea la carpeta de esta entrega
mkdir -p entregas/apellido_nombre/s11/codigo
# ... copia cuenta/ y notificacion/ (sin target/) y tu bitacora-s11.pdf ...
# Marca la carpeta para el commit
git add entregas/apellido_nombre/s11
# Guarda el cambio con un mensaje
git commit -m "Lab S11: persistencia políglota y caché"
# Súbelo
git push
```

**No abras pull request**: el push ES la entrega. Tarde no baja puntos, pero queda registrado en tu
tendencia (ver [`ENTREGAS.md`](../../ENTREGAS.md)).

## Preguntas de cierre

Respóndelas en la bitácora, cortas, con tus palabras:

1. ¿Por qué MongoDB para los avisos y **no** para el saldo? Usa lo que viste: la forma de cada aviso,
   cómo se leen y qué tendría que garantizar el saldo.
2. ¿Qué hit ratio midieron en A y en B, y por qué cambia? Con tus números de latencia y de lecturas
   a Postgres: ¿qué compraste con la caché, velocidad o capacidad?
3. ¿Qué pasaría si la autorización del cargo leyera el saldo de la caché? Usa lo que viste en la
   Parte 4.
4. Al aplicar un movimiento, ¿por qué se hace `DEL` de la copia y no `SET` con el saldo nuevo? ¿Y por
   qué después del commit y no antes?

## Si te atoras

- Si solo cambias un servicio, reconstruye solo ese: `docker compose up --build -d cuenta`.
- **`@Cacheable` no hace nada** (cada GET sigue saliendo como "leida de Postgres"): falta
  `@EnableCaching`, o pusiste la anotación en un método que se llama desde su misma clase.
- **En Redis el valor sale con caracteres raros** (`\xac\xed...`): falta el serializador JSON y Spring
  usó la serialización binaria de Java.
- **`SerializationException` o el GET da 500 después de la primera vez**: revisa que el serializador
  sea de `CuentaVista` y que uses el `ObjectMapper` que inyecta Spring (ese ya sabe escribir fechas).
- **El índice no aparece**: `crearIndice()` corre al arrancar; revisa el log de notificación
  (`docker compose logs notificacion | grep avisos`).
- **En Mongo `monto` sale como texto o como `Double`**: usa `new Decimal128(...)`.
- Para empezar de cero con Mongo: `docker compose exec mongo mongosh notificacion --eval 'db.avisos.drop()'`
  (borra la colección; el índice se crea otra vez al reiniciar notificación).
- Dudas o errores: abre un Issue en el repo del grupo con el error completo y el comando que
  corriste.
