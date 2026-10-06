# T03 Una implementación real de sharding: Redis Cluster

**Alumno:** Miyasaki Sato Yuichi Vicente
**Tema:** Redis Cluster 

> **La idea central:** Redis no reparte las llaves directamente entre servidores. Las reparte entre **16 384 *hash slots*** fijos, y lo que se reparte entre servidores son los slots.

---

## 1. Qué es y para qué se usa

Redis es una base de datos llave-valor que guarda todo en memoria RAM, por eso normalmente responde en menos de un milisegundo. Se usa sobre todo como caché, para sesiones de usuario, contadores, rankings, colas sencillas y *rate limiting*.

Un solo servidor Redis tiene un límite: la RAM de esa máquina y lo que aguanta un proceso. **Redis Cluster** es el modo de Redis que reparte los datos automáticamente entre varios nodos (sharding) y, si le pones réplicas, sigue funcionando aunque se caiga alguno. No es un producto aparte: cada nodo es un `redis-server` normal arrancado con `cluster-enabled yes`.

Cada nodo usa dos puertos: el de clientes (6379) y el del **cluster bus** (6379 + 10000 = 16379), por donde los nodos platican entre ellos. Se ve en la salida de `cluster nodes`: `172.28.0.11:6379@16379`.

## 2. Cómo reparte los datos

Es **por hash, con un paso intermedio**:

1. A cada llave se le calcula `slot = CRC16(llave) mod 16384`. Siempre hay exactamente 16 384 slots, sin importar cuántos nodos haya.
2. Cada nodo maestro es dueño de un conjunto de slots. Con 3 nodos, `redis-cli --cluster create` los reparte en rangos: 0–5460, 5461–10922 y 10923–16383.

```
llave "alumno:3"
     │   CRC16("alumno:3") mod 16384      ← fijo, nunca cambia
     ▼
slot 13725
     │   tabla slot → nodo                ← esto sí cambia (reshard)
     │   0–5460 → redis-1 | 5461–10922 → redis-2 | 10923–16383 → redis-3
     ▼
redis-3
```

O sea, *llave → slot* es por hash y es fijo; *slot → nodo* es una tabla que se puede cambiar. Por eso no es "hash mod número de nodos" (si lo fuera, al agregar un nodo cambiaría de lugar casi todo, ver la pregunta 4). La documentación aclara que tampoco es *consistent hashing*: es su propio esquema de slots.

**¿Quién elige la llave?** Son tres decisiones distintas:

- **La llave de partición la eligen los desarrolladores de la app**, porque en Redis la llave de partición *es el nombre de la llave*. No hay un "campo de partición" aparte.
- Si quieren que varias llaves queden juntas, usan **hash tags**: si la llave tiene `{...}`, solo se hashea lo que está entre las primeras llaves. `{carrito:42}:items` y `{carrito:42}:total` caen en el mismo slot (8596) porque las dos hashean solo `carrito:42`. Esto importa porque **las operaciones con varias llaves (MSET, MGET, transacciones, scripts Lua) solo funcionan si todas están en el mismo slot**; si no, Redis responde `CROSSSLOT` (demo, paso 4).
- **Redis decide el slot** (la función hash es fija) y **el administrador decide qué nodo tiene qué slots** (al crear el cluster y al hacer reshard o rebalance).

## 3. Cómo encuentra el shard correcto

**Todos los nodos tienen el mapa completo** de qué slots tiene cada quien. No hay servidor central de metadatos ni proxy: los nodos están conectados todos con todos por el cluster bus y se pasan el mapa por *gossip* (mensajes ping/pong que llevan qué slots sirve cada nodo y un número de versión, el *configEpoch*, para saber qué información es la más nueva).

Cuando llega una consulta:

1. El cliente calcula el slot de la llave (CRC16 lo puede calcular cualquiera) y manda el comando al nodo que cree que es el dueño.
2. Si le atinó, el nodo responde normal.
3. Si no, el nodo **no reenvía la consulta**: contesta con el error `MOVED <slot> <ip:puerto>` y el cliente reintenta en el nodo correcto. En la demo le pido `alumno:3` a redis-1 y responde `MOVED 13725 172.28.0.13:6379`.

Las librerías con soporte de cluster (para Python, Java, Node, etc.) **guardan una copia del mapa** (la piden con `CLUSTER SLOTS` o `CLUSTER SHARDS`) y van directo al nodo correcto. El `MOVED` solo aparece cuando su copia está vieja, y entonces la actualizan. `redis-cli -c` hace lo mismo: sigue los `MOVED` solo (en la demo se ven como `-> Redirected to slot [...]`).

**En corto:** el mapa lo guardan **todos los nodos**, y **cada cliente** tiene una copia en caché.

## 4. Qué pasa cuando agregas un shard

- El nodo nuevo entra con `redis-cli --cluster add-node` **vacío: 0 slots y 0 datos**. No se mueve nada automáticamente (demo, paso 5: redis-4 aparece con `0 keys | 0 slots`).
- Los datos se mueven solo cuando el administrador lo pide: con `--cluster reshard` (le dices cuántos slots, de qué nodo y a cuál) o con `--cluster rebalance --cluster-use-empty-masters` (empareja los slots entre todos).
- **Se mueven slots completos**, y con ellos todas sus llaves. Los demás slots ni se tocan.

**¿Cuántos datos?** Para que N+1 nodos queden parejos, cada nodo viejo le cede al nuevo una parte de lo suyo, y en total se mueve más o menos **1/(N+1)** de los datos. En la demo, de 3 a 4 nodos:

|                   | Antes (3 nodos) | Después (4 nodos)        |
|-------------------|-----------------|--------------------------|
| Slots por nodo    | ~5461           | 4096                     |
| Slots movidos     | —               | 4096 de 16 384 (25 %)    |
| Llaves movidas    | —               | 261 de 1000 (≈26 %)      |

Si se usara `hash(llave) mod N`, al pasar de 3 a 4 nodos cambiarían de nodo unas 3 de cada 4 llaves (con estas mismas 1000 llaves calculé 749). Esa es la ventaja de los slots: una llave nunca cambia de slot, lo que cambia es el dueño del slot.

**¿Cómo se mueven sin apagar nada?** Mientras un slot se mueve, el origen lo marca como `MIGRATING` y el destino como `IMPORTING`, y las llaves se van pasando en lotes con el comando `MIGRATE`. Si un cliente pide una llave que ya se fue, el origen responde `ASK` (una redirección temporal, solo para esa consulta) en vez de `MOVED`. Al terminar, el slot queda asignado al nodo nuevo. El cluster sigue atendiendo todo el tiempo; lo único que puede fallar un momento son comandos de varias llaves cuyo slot quedó a medias (responden `TRYAGAIN`).

Desde **Redis 8.4** existe además la **migración atómica de slots** (`CLUSTER MIGRATION IMPORT ...`): copia el slot completo como si fuera una réplica, sigue copiando las escrituras que llegan mientras tanto y cambia el dueño en un solo paso al final, así los clientes no ven redirecciones `ASK` a medias. Según Redis es bastante más rápida que mover llave por llave. Mi demo usa Redis 7.4, así que usa la forma clásica.

## 5. Qué hace con un shard caliente

**No lo detecta ni lo arregla solo.** No divide slots ni rebalancea por carga: `rebalance` empareja la *cantidad de slots* (opcionalmente con pesos, `--cluster-weight`), no el tráfico. Lo que da son herramientas para que lo resuelva el operador o el desarrollador. Hay dos casos:

**a) Un nodo tiene más carga que los demás por los slots que le tocaron.** Se mueven slots a mano a un nodo más libre (`--cluster reshard --cluster-from ... --cluster-to ... --cluster-slots ...`) o se rebalancea con pesos. Para encontrar el problema: `redis-cli --cluster info` (llaves por nodo), `CLUSTER COUNTKEYSINSLOT`, y desde Redis 8.2 `CLUSTER SLOT-STATS`, que por cada slot da número de llaves, CPU usado y bytes de red (por ejemplo `CLUSTER SLOT-STATS ORDERBY CPU-USEC DESC LIMIT 10`).

**b) Una sola llave, o un solo slot, recibe muchísimo tráfico.** Aquí mover slots no sirve: **el slot es la unidad mínima**, y una llave siempre vive en un solo maestro. Moverlo solo cambia el problema de nodo. En la demo lo provoqué a propósito: 300 llaves con el mismo hash tag `{tienda:7}` cayeron todas en el slot 15944, y redis-3 quedó con 550 llaves contra ~250 de los demás (paso 6). Lo que se hace:

- **Detectarla** con `redis-cli --hotkeys` (necesita que la política de memoria sea LFU).
- **Cambiar el diseño de las llaves:** no meter demasiadas llaves bajo el mismo hash tag; si una llave es muy popular, partirla en varias (por ejemplo `visitas:1` … `visitas:10`, que caen en slots distintos) y sumar en la app.
- **Leer de réplicas:** si el problema son lecturas, se le agregan réplicas a ese maestro y el cliente manda `READONLY` para poder leer de ellas, aceptando que a veces lea un dato un poco atrasado.
- **Caché del lado del cliente**, para no ir a Redis cada vez por la misma llave.


---

## Fuentes

- Redis. *Redis cluster specification*. https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec/ (slots y CRC16, hash tags, MOVED/ASK, gossip y cluster bus, migración en vivo, `READONLY`).
- Redis. *Scale with Redis Cluster*. https://redis.io/docs/latest/operate/oss_and_stack/management/scaling/ (puertos, crear el cluster, `add-node`, `reshard`, operaciones con varias llaves).
- Redis. *Redis CLI*. https://redis.io/docs/latest/develop/tools/cli/ (`-c`, `--hotkeys` y el requisito de LFU).
- Redis. *CLUSTER SLOT-STATS*. https://redis.io/docs/latest/commands/cluster-slot-stats/ (métricas por slot, desde Redis 8.2).
- Redis (2 de abril de 2026). *Atomic slot migration*. https://redis.io/blog/atomic-slot-migration/ (migración atómica en Redis 8.4).
- Redis. *Redis Anti-Patterns Every Developer Should Avoid*, sección "Hot keys". https://redis.io/learn/howtos/antipatterns
- Ayuda de `redis-cli --cluster help` (opciones de `reshard` y `rebalance`) y la demo de este repositorio.
- Usé Claude como apoyo para preparar la demo y revisar la redacción.
