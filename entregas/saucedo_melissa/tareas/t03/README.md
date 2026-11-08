# T03 · Una implementación real de sharding: Citus (PostgreSQL)

**Alejandra Melissa Saucedo Serralde · Sistemas Distribuidos 2027-1 · Grupo 2**

Elegí **Citus**, una extensión de PostgreSQL que reparte tablas entre varios servidores. La escogí porque en Mexi Banco ya usamos Postgres y me interesaba ver cómo se le agrega sharding a una base que ya conozco. Respondo las cinco preguntas de la tarea, y al final van las fuentes.

---

## 1. ¿Qué es y para qué se usa?

Citus es una **extensión de PostgreSQL** que convierte varios servidores Postgres en un solo clúster. Una tabla se parte en pedazos (*shards*) que viven en distintos servidores, y las consultas SQL se siguen escribiendo igual. La documentación dice que Citus tiene "100% SQL support" para las consultas que tocan un solo nodo del clúster.

El clúster tiene dos tipos de nodo: un **coordinador**, que recibe las consultas y guarda la información de dónde está cada dato, y los **workers**, que guardan los shards. Se usa sobre todo en dos casos que menciona su documentación: aplicaciones **multi-tenant** (muchos clientes, cada uno con sus datos) y cargas de tiempo real con muchos datos.

Dato de contexto: desde la versión 11 (junio 2022) **todo Citus es código abierto**, incluidos el rebalanceador sin bloqueo de escrituras y el aislamiento de tenants. Esto lo aclaro porque en documentación de versiones viejas (por ejemplo la v9) esas funciones aparecen como solo de la edición Enterprise.

## 2. ¿Cómo reparte los datos y quién elige la llave?

- **Método:** por **hash**. Al llamar `create_distributed_table`, si no se indica método, el valor por defecto es hash. El hash de la columna de distribución cae en un rango, y cada shard se queda con un rango de valores.
- **Quién elige la llave:** **la persona que diseña la tabla**. La documentación dice que hay que escoger "una sola columna por tabla" como *distribution column*; Citus no la adivina. Elegirla bien es la decisión de diseño más importante.
- **Cuántos shards:** también se configura (`citus.shard_count`, o el parámetro `shard_count` de `create_distributed_table`, entre 1 y 64000). El FAQ recomienda entre 32 y 128 shards para cargas multi-tenant, y de 2 a 4 veces el número de núcleos para cargas de tiempo real. La razón de tener más shards que nodos es poder mover shards después.
- **Rangos de hash:** cada shard tiene un rango de valores de hash contiguo, sin huecos y sin empalmes. Por ejemplo, la documentación muestra los shards 102026 (de 268435456 a 402653183) y 102027 (que empieza en 402653184).
- **Otras formas:** además de las tablas por hash existen las **tablas de referencia**, que se copian completas en todos los nodos (sirven para tablas chicas que se unen con las grandes). También menciona un método `append`, pero lo vi solo en documentación vieja y no lo revisé a fondo.
- **Co-ubicación:** las tablas distribuidas con el mismo tipo de llave y el mismo número de shards quedan **juntas** por defecto, así un JOIN por la llave se resuelve dentro de un solo nodo.

## 3. ¿Cómo encuentra el shard correcto?

**El mapa lo guarda el coordinador**, en tablas de metadatos dentro de Postgres:

| Tabla | Qué guarda |
|---|---|
| `pg_dist_partition` | Qué tablas están distribuidas, el método (`h` hash, `a` append, `n` referencia) y la columna de distribución |
| `pg_dist_shard` | Cada shard: su `shardid`, la tabla a la que pertenece y su rango `shardminvalue` a `shardmaxvalue` |
| `pg_dist_placement` | En qué grupo de nodos vive cada shard (la "ubicación" del shard) |
| `pg_dist_node` | Los workers: host, puerto, si están activos y si deben recibir shards |

Cuando llega una consulta con la llave (`WHERE llave = X`), el coordinador calcula el hash de `X`, busca en `pg_dist_shard` qué rango lo contiene, busca en `pg_dist_placement` en qué nodo está ese shard y manda la consulta solo a ese nodo. La documentación dice que los valores mínimo y máximo de cada shard se usan para **descartar shards que no hacen falta** (*pruning*) en las consultas `SELECT`.

También hay una vista práctica, `citus_shards`, que muestra dónde está cada shard (nodo y puerto) y cuánto pesa.

**Cambio importante desde Citus 11:** el esquema y los metadatos se **sincronizan del coordinador a todos los nodos**, así que cualquier nodo puede recibir consultas distribuidas. Las aplicaciones pueden seguir usando solo el coordinador.

## 4. ¿Qué pasa cuando agregas un shard (un worker nuevo)?

Aquí hay que separar dos cosas, porque en Citus se agregan **nodos**, no shards sueltos:

- **Al agregar el worker no se mueve nada.** La documentación lo dice así: "Existing shards will stay where they are unless redistributed". El nodo nuevo solo recibe shards de las tablas que se creen **después**. El FAQ agrega que el nodo nuevo "will not contain any data (shards)".
- **Los datos viejos se mueven con el rebalanceador.** En la versión 12.1 se usa `citus_rebalance_start()`, que reparte los shards de forma pareja entre los workers (puede usar distintas estrategias; en lo que revisé no las describen). Por defecto mueve **un shard a la vez**, y los shards de un mismo grupo de co-ubicación se mueven siempre en secuencia.
- **Sin bloquear escrituras:** desde Citus 11 el rebalanceo usa **replicación lógica**, así que las escrituras no se bloquean salvo un instante breve al cambiar la copia nueva en los metadatos. Para eso las tablas necesitan llave primaria (o *replica identity*).
- **¿Cuántos datos se mueven?** La documentación que revisé **no da un número**. Lo que sí dice es la meta: dejar los shards repartidos de forma pareja. En mi prueba con Docker (sección del opcional), con 8 shards y 2 workers, el rebalanceador agendó **4 movimientos**: movió 4 shards completos, no filas sueltas, y los 20 datos siguieron completos.

## 5. ¿Qué hace con un shard caliente?

La documentación **no usa el término "shard caliente"**, pero sí habla del problema equivalente en multi-tenant: como los tenants comparten shards según el hash de su valor, un tenant muy grande puede causar **contención de recursos** para los demás que viven en su shard.

Lo que ofrece Citus para eso:

- **`isolate_tenant_to_new_shard`:** crea un shard nuevo solo para ese tenant, mueve ahí sus filas y parte el shard viejo alrededor de ese rango. El shard nuevo nace en el mismo nodo; para ponerlo en otro se usa `citus_move_shard_placement()`. Con `CASCADE` lo hace también en las tablas co-ubicadas. Está disponible en la edición abierta desde la versión 11.
- **Mover al tenant grande a un nodo propio:** es la recomendación de la documentación.
- **`citus_shards`:** sirve para ver si hay desbalance de tamaño entre nodos.

Lo que **no** vi: que Citus detecte un shard caliente por sí solo y lo reparta. Hay que detectarlo y actuar a mano. Además, si la carga está pegada a **una sola llave** (un solo cliente que genera todo el tráfico), el hash no ayuda: ese valor siempre cae en el mismo shard.

## Opcional: Citus en Docker, con dos workers

Levanté un clúster con **1 coordinador y 2 workers** (imagen `citusdata/citus:12.1`), repartí una tabla en 8 shards y fui viendo en qué worker quedaba cada dato. Todo salió de mi terminal; abajo van los comandos y la salida real.

### 1. Levantar el clúster con un solo worker

`docker-compose.yml`:

```yaml
services:
  coordinador:
    image: citusdata/citus:12.1
    environment:
      POSTGRES_PASSWORD: citus
      POSTGRES_HOST_AUTH_METHOD: trust
  worker1:
    image: citusdata/citus:12.1
    environment:
      POSTGRES_PASSWORD: citus
      POSTGRES_HOST_AUTH_METHOD: trust
  worker2:
    image: citusdata/citus:12.1
    environment:
      POSTGRES_PASSWORD: citus
      POSTGRES_HOST_AUTH_METHOD: trust
```

(Las contraseñas son de laboratorio: `trust` deja entrar sin clave, así que esto no es para producción.)

```bash
docker compose up -d coordinador worker1
docker compose exec -T coordinador psql -U postgres -c "SELECT * FROM citus_add_node('worker1', 5432);" -c "SELECT nodeid, nodename, nodeport, isactive, shouldhaveshards FROM pg_dist_node;"
```

```
 citus_add_node
----------------
              1
(1 row)

 nodeid | nodename | nodeport | isactive | shouldhaveshards
--------+----------+----------+----------+------------------
      1 | worker1  |     5432 | t        | t
(1 row)
```

La imagen ya trae la extensión instalada (al hacer `CREATE EXTENSION IF NOT EXISTS citus` solo avisa que ya existe).

### 2. Crear una tabla distribuida por `id`

```bash
docker compose exec -T coordinador psql -U postgres \
  -c "SET citus.shard_count = 8;" \
  -c "CREATE TABLE clientes (id bigint PRIMARY KEY, nombre text);" \
  -c "SELECT create_distributed_table('clientes', 'id');" \
  -c "INSERT INTO clientes SELECT i, 'cliente ' || i FROM generate_series(1,20) i;" \
  -c "SELECT count(*) FROM clientes;"
```

```
SET
CREATE TABLE
 create_distributed_table
--------------------------

(1 row)

INSERT 0 20
 count
-------
    20
(1 row)
```

### 3. ¿En qué shard quedó cada dato?

```bash
docker compose exec -T coordinador psql -U postgres \
  -c "SELECT shardid, nodename, shard_size FROM citus_shards WHERE table_name::text = 'clientes' ORDER BY shardid;" \
  -c "SELECT shardid, shardminvalue, shardmaxvalue FROM pg_dist_shard WHERE logicalrelid = 'clientes'::regclass ORDER BY shardid;" \
  -c "SELECT get_shard_id_for_distribution_column('clientes', id) AS shardid, string_agg(id::text, ',' ORDER BY id) AS ids FROM clientes GROUP BY 1 ORDER BY 1;"
```

```
 shardid | nodename | shard_size
---------+----------+------------
  102008 | worker1  |      32768
  102009 | worker1  |      32768
  102010 | worker1  |      32768
  102011 | worker1  |      32768
  102012 | worker1  |      16384
  102013 | worker1  |      32768
  102014 | worker1  |      32768
  102015 | worker1  |      32768
(8 rows)

 shardid | shardminvalue | shardmaxvalue
---------+---------------+---------------
  102008 | -2147483648   | -1610612737
  102009 | -1610612736   | -1073741825
  102010 | -1073741824   | -536870913
  102011 | -536870912    | -1
  102012 | 0             | 536870911
  102013 | 536870912     | 1073741823
  102014 | 1073741824    | 1610612735
  102015 | 1610612736    | 2147483647
(8 rows)

 shardid |     ids
---------+--------------
  102008 | 1,8,15,20
  102009 | 5,10
  102010 | 4,7,14,16,17
  102011 | 3,19
  102013 | 6,13
  102014 | 2
  102015 | 9,11,12,18
(7 rows)
```

Lo que se ve:
- Los 8 shards viven en `worker1`, porque es el único worker.
- Los rangos de hash son contiguos, de -2147483648 a 2147483647, sin huecos ni empalmes.
- El hash no reparte parejo cuando hay pocas filas: el shard 102012 quedó vacío y el 102014 tiene un solo cliente.

### 4. Agregar el segundo worker: no se mueve nada

```bash
docker compose up -d worker2
docker compose exec -T coordinador psql -U postgres \
  -c "SELECT * FROM citus_add_node('worker2', 5432);" \
  -c "SELECT nodename, count(*) AS shards FROM citus_shards WHERE table_name::text = 'clientes' GROUP BY nodename ORDER BY nodename;"
```

```
 citus_add_node
----------------
              2
(1 row)

 nodename | shards
----------+--------
 worker1  |      8
(1 row)
```

El worker nuevo ya está en el clúster, pero **los 8 shards siguen en `worker1`**. Es lo que dice la documentación: los shards existentes se quedan donde están hasta que se redistribuyen.

### 5. Rebalancear

Mi primer intento **falló**. `citus_rebalance_start()` agendó 4 movimientos, pero la espera (`citus_rebalance_wait()`) no regresaba. Al revisar la tarea en segundo plano, el error era:

```
 task_id |  status  |                              message
---------+----------+-------------------------------------------------------------------
       1 | runnable | ERROR: ERROR:  logical decoding requires wal_level >= logical
```

El rebalanceo sin bloqueo de escrituras usa **replicación lógica**, y los nodos de la imagen arrancan con un `wal_level` menor. Lo arreglé cancelando el trabajo, subiendo `wal_level` en los tres nodos y reiniciándolos:

```bash
docker compose exec -T coordinador psql -U postgres -c "SELECT citus_job_cancel(1);"
for n in coordinador worker1 worker2; do docker compose exec -T $n psql -U postgres -c "ALTER SYSTEM SET wal_level = logical;"; done
docker compose restart coordinador worker1 worker2
docker compose exec -T coordinador psql -U postgres -c "SHOW wal_level;" -c "SELECT count(*) FROM clientes;"
```

```
 wal_level
-----------
 logical

 count
-------
    20
```

Con eso, el segundo intento sí terminó:

```bash
docker compose exec -T coordinador psql -U postgres \
  -c "SELECT citus_rebalance_start();" -c "SELECT citus_rebalance_wait();" \
  -c "SELECT nodename, count(*) AS shards FROM citus_shards WHERE table_name::text = 'clientes' GROUP BY nodename ORDER BY nodename;"
```

```
NOTICE:  Scheduled 4 moves as job 2
 ...
 nodename | shards
----------+--------
 worker1  |      4
 worker2  |      4
(2 rows)
```

Y así quedó cada shard, con los datos completos y una consulta por `id` funcionando igual:

```
 shardid | nodename
---------+----------
  102008 | worker2
  102009 | worker2
  102010 | worker2
  102011 | worker2
  102012 | worker1
  102013 | worker1
  102014 | worker1
  102015 | worker1

 count: 20          SELECT * FROM clientes WHERE id = 7  ->  7 | cliente 7
```

**Qué muestra esto para la pregunta 4:** el rebalanceador movió **4 de los 8 shards, completos**, y no filas sueltas; los 20 clientes siguen ahí. Con 8 shards y 2 workers lo parejo es 4 y 4.

### 6. Aislar a un "cliente caliente"

Supongamos que el cliente `1` genera casi todo el tráfico. Lo aislé en un shard propio con `isolate_tenant_to_new_shard`:

```bash
docker compose exec -T coordinador psql -U postgres \
  -c "SELECT isolate_tenant_to_new_shard('clientes', 1, shard_transfer_mode => 'force_logical');" \
  -c "SELECT get_shard_id_for_distribution_column('clientes', 1) AS shard_del_cliente_1;" \
  -c "SELECT s.shardid, s.shardminvalue, s.shardmaxvalue, p.nodename FROM pg_dist_shard s JOIN citus_shards p ON p.shardid = s.shardid WHERE s.logicalrelid = 'clientes'::regclass AND s.shardminvalue = s.shardmaxvalue;" \
  -c "SELECT count(*) FROM clientes;"
```

```
 isolate_tenant_to_new_shard
-----------------------------
                      102017

 shard_del_cliente_1
---------------------
              102017

 shardid | shardminvalue | shardmaxvalue | nodename
---------+---------------+---------------+----------
  102017 | -1905060026   | -1905060026   | worker2

 count: 20
```

El cliente `1` quedó en el shard **102017**, cuyo rango tiene **un solo valor de hash** (`-1905060026`), en `worker2`. Los demás datos siguen completos (20). Ese shard ya se puede mover a un nodo propio con `citus_move_shard_placement()` si hace falta.

Para apagar todo sin dejar basura: `docker compose down -v` (aquí sí se puede usar `-v`, porque esta carpeta es solo de la tarea).

---

## Fuentes

- [Cluster Management, Citus 12.1 (agregar workers, rebalanceo, tenants grandes)](https://docs.citusdata.com/en/v12.1/admin_guide/cluster_management.html)
- [Cluster Management, Citus 9.3 (comparación con la versión vieja)](https://docs.citusdata.com/en/v9.3/admin_guide/cluster_management.html)
- [Citus Tables and Views, 13.0 (`pg_dist_*` y `citus_shards`)](https://docs.citusdata.com/en/stable/develop/api_metadata.html)
- [Citus Utility Functions, 13.0 (`create_distributed_table`, `shard_count`)](https://docs.citusdata.com/en/stable/develop/api_udf.html?highlight=shard_count)
- [Metadata tables, Citus 9.2 (`partmethod` h, a, n)](https://github.com/citusdata/citus_docs/blob/v9.2/develop/api_metadata.rst)
- [Citus FAQ, 12.1 (llave de distribución, nodos nuevos, metadatos)](https://github.com/citusdata/citus_docs/blob/v12.1/faq/faq.rst)
- [Citus 11 goes fully open source (metadatos en todos los nodos, rebalanceo sin bloqueo)](https://www.citusdata.com/blog/2022/06/17/citus-11-goes-fully-open-source/)

Me apoyé en IA para buscar las páginas y armar el borrador, y revisé cada afirmación contra las fuentes de arriba.
