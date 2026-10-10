# T03: Sharding con Redis Cluster

Cuando una aplicación guarda más datos de los que un solo servidor puede atender cómodamente, una opción es partir esos datos entre varias máquinas. A esa técnica se le llama **sharding**. Cada partición se llama *shard* y conserva sólo una parte del conjunto total. La idea es que cada servidor atienda menos datos y menos operaciones, mientras el sistema en conjunto puede crecer agregando nodos.

Para esta tarea elegí **Redis Cluster**. Redis normalmente se conoce como una base de datos en memoria que trabaja con claves y valores. Redis Cluster es la modalidad distribuida de Redis: permite repartir las claves entre varios nodos master sin que exista un proxy central obligatorio para decidir a dónde debe llegar cada operación.

## 1. Qué es Redis Cluster y para qué se usa

Redis Cluster es una forma de ejecutar Redis como un conjunto de nodos coordinados. Se usa cuando una sola instancia ya no tiene suficiente memoria, capacidad de procesamiento o disponibilidad para atender una aplicación. En lugar de guardar todas las claves en un solo proceso, reparte las claves entre varios masters. También permite tener réplicas, aunque en esta demostración se usan solamente tres masters para concentrarse en el sharding.

Redis no maneja tablas ni colecciones como una base de datos relacional o MongoDB. La colección de este ejercicio es lógica: se trata de varias claves de usuario, y cada usuario se guarda como un hash de Redis con campos como nombre, correo y ciudad.

## 2. Cómo reparte los datos

Redis Cluster hace partición por **hash**. Divide el espacio de claves en 16,384 *hash slots*. Para una clave normal calcula:

```text
slot = CRC16(clave) mod 16384
```

Cada master es dueño de cierto intervalo de slots. Por ejemplo, en un clúster nuevo de tres masters, uno atiende aproximadamente los slots 0--5460, otro 5461--10922 y otro 10923--16383. Por ello, no se decide manualmente que una clave vaya a un nodo: el nombre de la clave determina su slot y la asignación de slots determina el master que la atiende.

La aplicación elige el nombre de las claves. Si necesita que varias claves queden juntas, puede usar un **hash tag**. En una clave como `orden:{100}:detalle`, Redis calcula el hash solamente con `100`; las claves que compartan `{100}` quedan en el mismo slot. Esto sirve para operaciones de varias claves, pero abusar de un mismo tag puede concentrar demasiado trabajo en un shard.

## 3. Cómo encuentra el shard correcto

Cada nodo guarda un mapa de slots hacia masters y ese mapa se propaga entre el clúster. Un cliente compatible con Redis Cluster también aprende el mapa. Puede calcular primero el slot de la clave y enviar la operación directamente al master correcto.

Si un cliente llega a otro nodo, ese nodo responde con `MOVED`, junto con el slot y la dirección del master dueño. El cliente debe repetir la operación en esa dirección y normalmente guarda el dato para las siguientes consultas. Así Redis Cluster no necesita un router central que sea un punto único de falla.

## 4. Qué pasa al agregar un shard

Agregar un nodo master no mueve automáticamente todas las claves. El administrador agrega primero un master vacío y después hace **resharding**: mueve una cantidad elegida de hash slots desde los masters existentes hacia el nuevo master. Al mover un slot se mueven las claves que tienen hash en ese slot. Por lo tanto, se mueve sólo la fracción de datos de los slots reasignados, no todo el clúster.

Durante la migración, Redis usa estados de slot de importación y migración. Las claves existentes se transfieren de manera controlada y el cliente puede recibir redirecciones temporales. Cuando termina, el mapa de slots se propaga y los clientes actualizan su ruta.

## 5. Qué ocurre con un shard caliente

Redis Cluster distribuye bien claves variadas, pero no detecta ni redistribuye por sí solo un shard caliente basándose en carga. Si muchas operaciones usan claves con el mismo slot, ese master puede quedar saturado aunque otros estén poco utilizados. El operador puede hacer resharding para mover slots, pero si la carga proviene de una sola clave muy popular, moverla sólo cambia el nodo saturado.

La aplicación debe diseñar las claves con cuidado: evitar hash tags demasiado generales, repartir los datos de una clave caliente en varias claves cuando sea posible y vigilar la carga por nodo. Las réplicas también pueden ayudar a escalar lecturas que toleren datos potencialmente atrasados, pero no distribuyen automáticamente las escrituras de una clave caliente.

---

# Práctica con Docker: tres shards de Redis

## Objetivo

Levantar tres masters de Redis Cluster, crear una colección lógica de usuarios y comprobar qué slot y qué shard atienden cada clave.

## Requisitos

- Docker Desktop iniciado y con el motor Linux en ejecución.
- Docker Compose v2 o posterior.

La configuración está en [docker-compose.yml](docker-compose.yml). Los nodos se comunican por la red interna `redis-cluster`; por eso los comandos se ejecutan dentro de `redis-1` y no se publican puertos en Windows.

## 1. Levantar los nodos y crear el clúster

```bash
docker compose up -d
docker compose exec redis-1 redis-cli --cluster create redis-1:6379 redis-2:6379 redis-3:6379 --cluster-replicas 0 --cluster-yes
docker compose exec redis-1 redis-cli --cluster check redis-1:6379
```

La última orden debe indicar que los 16,384 slots están cubiertos. Los tres contenedores son masters, sin réplicas, para que cada uno represente un shard.

## 2. Crear la colección lógica de usuarios

```bash
docker compose exec redis-1 redis-cli -c HSET usuario:ana nombre Ana correo ana@ejemplo.mx ciudad CDMX
docker compose exec redis-1 redis-cli -c HSET usuario:beto nombre Beto correo beto@ejemplo.mx ciudad Puebla
docker compose exec redis-1 redis-cli -c HSET usuario:carla nombre Carla correo carla@ejemplo.mx ciudad Oaxaca
docker compose exec redis-1 redis-cli -c HSET usuario:diego nombre Diego correo diego@ejemplo.mx ciudad Merida
docker compose exec redis-1 redis-cli -c HSET usuario:elena nombre Elena correo elena@ejemplo.mx ciudad Toluca
docker compose exec redis-1 redis-cli -c HSET usuario:fer nombre Fernando correo fer@ejemplo.mx ciudad Merida
```

La opción `-c` hace que `redis-cli` siga automáticamente las redirecciones `MOVED` hacia el master dueño de cada clave.

## 3. Mostrar slots y shards

```bash
docker compose exec redis-1 redis-cli CLUSTER NODES
docker compose exec redis-1 redis-cli CLUSTER KEYSLOT usuario:ana
docker compose exec redis-1 redis-cli CLUSTER KEYSLOT usuario:beto
docker compose exec redis-1 redis-cli CLUSTER KEYSLOT usuario:carla
docker compose exec redis-1 redis-cli -c HGETALL usuario:ana
```

Para mostrar la redirección que identifica al shard dueño, se puede consultar sin `-c`:

```bash
docker compose exec redis-1 redis-cli HGETALL usuario:ana
```

Si `usuario:ana` pertenece a otro master, la salida tendrá la forma `MOVED <slot> <direccion>:6379`. La dirección y el intervalo de slots de `CLUSTER NODES` permiten relacionar cada usuario con su shard.

## Evidencia real de ejecución

El clúster se creó correctamente con tres masters y sin réplicas. La distribución inicial de slots fue:

```text
Master[0] -> Slots 0 - 5460
Master[1] -> Slots 5461 - 10922
Master[2] -> Slots 10923 - 16383
[OK] All nodes agree about slots configuration.
[OK] All 16384 slots covered.
```

Después de crear los usuarios, `CLUSTER NODES` mostró los tres shards activos:

```text
172.24.0.4:6379@16379  myself,master  connected 0-5460
172.24.0.3:6379@16379  master         connected 5461-10922
172.24.0.2:6379@16379  master         connected 10923-16383
```

Las consultas de slots y la redirección de Redis demostraron el reparto real de la colección lógica:

```text
CLUSTER KEYSLOT usuario:ana    -> 14664
CLUSTER KEYSLOT usuario:beto   -> 11612
CLUSTER KEYSLOT usuario:carla  ->   915

HGETALL usuario:ana, enviado sin -c desde redis-1:
(error) MOVED 14664 172.24.0.2:6379
```

| Clave | Slot obtenido con `CLUSTER KEYSLOT` | Shard/master que la atiende |
| --- | ---: | --- |
| `usuario:ana` | 14664 | `172.24.0.2:6379`, intervalo 10923--16383 |
| `usuario:beto` | 11612 | `172.24.0.2:6379`, intervalo 10923--16383 |
| `usuario:carla` | 915 | `redis-1` (`172.24.0.4:6379`), intervalo 0--5460 |

Como comprobación del contenido, la consulta con el cliente en modo Cluster devolvió los campos de Ana:

```text
nombre  Ana
correo  ana@ejemplo.mx
ciudad  CDMX
```

## Limpieza opcional

```bash
docker compose down -v
```

Este comando elimina los contenedores **y los volúmenes**; por ello borra los datos y la configuración del clúster creados para esta práctica.

## Fuentes

- [Redis Cluster specification](https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec/): hash slots, hash tags, `MOVED`, mapa de slots, migración y resharding.
- [Scale with Redis Cluster](https://redis.io/docs/latest/operate/oss_and_stack/management/scaling/): creación de clúster, verificación, añadir nodos y resharding con `redis-cli`.
- [CLUSTER KEYSLOT](https://redis.io/docs/latest/commands/cluster-keyslot/): consulta del slot asociado a una clave.
