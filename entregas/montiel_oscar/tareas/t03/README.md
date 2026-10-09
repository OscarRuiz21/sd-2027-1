# T03 - Una implementación real de sharding: Redis Cluster

Elegí **Redis Cluster** porque permite ver el reparto con números: una clave produce un *slot* y ese slot pertenece a un nodo. Redis guarda datos como pares **clave-valor**; aquí las claves `cuenta:*` forman mi colección lógica de cuentas. El ejemplo con Docker está al final.

## 1. ¿Qué es y para qué se usa?

Redis es un almacén de datos que permite consultar y modificar valores por su clave. Redis Cluster reparte esas claves entre varios servidores o *shards*. Se usa cuando los datos o las peticiones ya no convienen en un solo servidor. Por ejemplo, una aplicación puede guardar sesiones de muchos usuarios y repartirlas entre varios nodos.

**Analogía:** si hay tres archiveros, no guardo todos los expedientes en uno. Uso una regla para decidir a cuál archivero va cada expediente.

## 2. ¿Cómo reparte los datos y quién elige la llave?

La aplicación elige el **nombre de la clave**, por ejemplo `cuenta:ana`. Redis calcula un número entre 0 y 16383 con esta regla:

```text
slot = CRC16(nombre_de_la_clave) mod 16384
```

Luego el clúster asigna **rangos de slots** a los nodos. Es reparto por **hash**, con un paso intermedio: `clave -> slot -> nodo`. No calcula `hash mod número de nodos`; por eso el número de slots sigue siendo 16384 aunque se agreguen nodos. El administrador decide qué rangos de slots posee cada nodo, mientras Redis calcula el slot de cada clave.

**Detalle útil:** si dos nombres contienen la misma etiqueta entre llaves, como `cuenta:{ana}:saldo` y `cuenta:{ana}:perfil`, Redis calcula el slot usando `ana`. Eso permite mantener juntas claves relacionadas, pero abusar de la misma etiqueta puede concentrar carga.

## 3. ¿Cómo encuentra el shard correcto?

Los nodos conocen el mapa **slot -> nodo** y se lo comunican entre sí. Un cliente preparado para Redis Cluster puede obtenerlo con `CLUSTER SHARDS` y mandar cada petición al nodo correcto. Si pregunta a otro nodo, recibe una respuesta `MOVED` con el slot y la dirección correcta; después puede actualizar su mapa.

**Analogía:** el número de expediente indica en qué sección buscar; el directorio del edificio dice qué archivero guarda esa sección.

## 4. ¿Qué ocurre al agregar un shard?

Un nodo nuevo entra sin slots y, por tanto, sin datos de esa colección. Para darle trabajo se le transfieren algunos slots desde los nodos existentes (*resharding*). **Sólo se mueven las claves que pertenecen a esos slots**, no toda la base. Si se trasladan 4096 de 16384 slots, se traslada una cuarta parte de los slots; el número exacto de claves movidas depende de cómo estén distribuidas. En Redis Cluster abierto, el operador debe iniciar este rebalanceo; agregar el nodo por sí solo no reparte automáticamente las claves existentes.

## 5. ¿Qué pasa con un shard caliente?

Un shard caliente recibe más trabajo que los demás. Redis Cluster no divide automáticamente una sola clave muy solicitada entre varios nodos: esa clave pertenece a un único slot, y ese slot tiene un único nodo principal. Si hay **muchas claves** en slots cargados, se pueden mover algunos slots a otro nodo. Si el problema es **una sola clave** muy solicitada, moverla sólo cambia el cuello de botella de lugar; hay que revisar el diseño de las claves, dividir el dato o reducir las lecturas con caché en la aplicación. Las réplicas pueden ayudar con lecturas si se aceptan datos quizá desactualizados, pero esta prueba no configuró réplicas.

## Prueba opcional con Docker: tres shards reales

El archivo `docker-compose.yml` inicia tres nodos Redis 8. No publica puertos en la computadora: los comandos se ejecutan dentro de los contenedores. Es una **demostración**, no una configuración para producción, porque tiene tres nodos principales y ninguna réplica.

Desde esta carpeta, ejecuté:

```bash
docker compose up -d --wait
docker compose exec -T redis-1 redis-cli --cluster create redis-1:6379 redis-2:6379 redis-3:6379 --cluster-replicas 0 --cluster-yes
```

La creación se hace **una sola vez**; al reiniciar los contenedores se conserva el estado en los volúmenes. La salida relevante fue:

```text
redis-1 -> slots 0-5460
redis-2 -> slots 5461-10922
redis-3 -> slots 10923-16383
[OK] All 16384 slots covered.
```

Inserté tres cuentas. La opción `-c` hace que `redis-cli` siga las redirecciones del clúster:

```bash
docker compose exec -T redis-1 redis-cli -c SET cuenta:ana 100
docker compose exec -T redis-1 redis-cli -c SET cuenta:beto 200
docker compose exec -T redis-1 redis-cli -c SET cuenta:carla 300
```

Las tres respuestas fueron `OK`. Consulté el slot de cada clave:

```bash
docker compose exec -T redis-1 redis-cli CLUSTER KEYSLOT cuenta:ana
docker compose exec -T redis-1 redis-cli CLUSTER KEYSLOT cuenta:beto
docker compose exec -T redis-1 redis-cli CLUSTER KEYSLOT cuenta:carla
```

| Clave | Valor | Slot observado | Nodo responsable |
|---|---:|---:|---|
| `cuenta:ana` | 100 | 11856 | `redis-3` |
| `cuenta:beto` | 200 | 8014 | `redis-2` |
| `cuenta:carla` | 300 | 1922 | `redis-1` |

Por último pregunté **directamente a cada nodo**, sin `-c`, para comprobar que realmente guarda su dato:

```bash
docker compose exec -T redis-1 redis-cli GET cuenta:carla
# 300
docker compose exec -T redis-2 redis-cli GET cuenta:beto
# 200
docker compose exec -T redis-3 redis-cli GET cuenta:ana
# 100
docker compose exec -T redis-1 redis-cli GET cuenta:ana
# MOVED 11856 172.22.0.3:6379
```

La última respuesta demuestra que `redis-1` **no** guarda `cuenta:ana`: conoce el nodo correcto y devuelve `MOVED`. La IP es interna de Docker y puede cambiar en otra computadora; lo importante es el slot 11856 y el nodo dueño. Para revisar el estado se puede usar:

```bash
docker compose exec -T redis-1 redis-cli CLUSTER INFO
docker compose exec -T redis-1 redis-cli CLUSTER NODES
docker compose exec -T redis-1 redis-cli --cluster check redis-1:6379
```

El chequeo final confirmó que los tres datos quedaron repartidos entre tres nodos principales:

```text
redis-1:6379 (109db308...) -> 1 keys | 5461 slots | 0 slaves.
172.22.0.4:6379 (20c03666...) -> 1 keys | 5462 slots | 0 slaves.
172.22.0.3:6379 (fa78ac6c...) -> 1 keys | 5461 slots | 0 slaves.
[OK] 3 keys in 3 masters.
[OK] All nodes agree about slots configuration.
[OK] All 16384 slots covered.
```

Al terminar: `docker compose down`. Este comando conserva los volúmenes y permite repetir las consultas después de volver a levantar los contenedores.


## Fuentes

1. Redis, [Scale with Redis Cluster](https://redis.io/docs/latest/operate/oss_and_stack/management/scaling/). Explica los 16384 slots, el reparto, la creación del clúster y el resharding.
2. Redis, [Redis cluster specification](https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec/). Describe CRC16, `MOVED`, el mapa de slots y la propiedad de cada clave.
3. Redis, [CLUSTER SHARDS](https://redis.io/docs/latest/commands/cluster-shards/) y [CLUSTER KEYSLOT](https://redis.io/docs/latest/commands/cluster-keyslot/). Documentan los comandos para consultar el mapa y el slot.
