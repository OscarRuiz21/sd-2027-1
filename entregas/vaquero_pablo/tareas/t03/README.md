# T03 · Sharding con Citus y PostgreSQL

**Pablo Vaquero · Sistemas Distribuidos · Grupo 2 · 9 de octubre de 2026**

Elegí Citus porque permite estudiar el reparto de una tabla sin abandonar SQL y PostgreSQL. El ejemplo es una tabla de movimientos agrupados por cliente: sirve para ver la diferencia entre repartir los datos y levantar varias copias de un servicio, como en el S07.

## 1. Qué es y para qué se usa

Citus es una extensión de PostgreSQL que permite distribuir tablas y ejecutar consultas entre varios servidores. Se usa, por ejemplo, en aplicaciones que atienden a muchas organizaciones y en análisis de grandes cantidades de eventos. En este trabajo uso **sharding por filas**: cada shard guarda una parte de la tabla, y todos juntos forman la tabla completa. Citus también ofrece distribución por esquemas, pero no es la modalidad de esta demostración. [Conceptos de Citus](https://docs.citusdata.com/en/v13.0/get_started/concepts.html)

Un **shard** es una parte lógica de los datos; un **worker** es el servidor que la almacena. Un worker puede guardar varios shards. Repartir una tabla no implica que haya copias de respaldo: esta demo no tiene réplicas para tolerar la caída de un nodo.

## 2. Cómo reparte los datos y quién elige la llave

En la modalidad elegida, Citus reparte **por hash**. Quien diseña la aplicación decide la columna de distribución y la declara al crear la tabla distribuida. Citus calcula un hash de su valor y busca el shard cuyo intervalo de valores hash lo contiene. Aunque esos intervalos son rangos, no son rangos del ID original: no significa que los clientes 1–100 vayan a un servidor y los 101–200 a otro. [Creación de tablas distribuidas](https://docs.citusdata.com/en/v13.0/develop/api_udf.html#create-distributed-table) · [Metadatos de los shards](https://docs.citusdata.com/en/v13.0/develop/api_metadata.html#shard-table)

Para `movimientos` elegí `cliente_id`. Así, los movimientos del mismo cliente quedan juntos. Elegir una columna con muchos valores distintos y sin una concentración excesiva ayuda al reparto; una columna como `estatus`, con solo tres valores, puede dejar gran parte del trabajo en pocos shards. La decisión debe considerar las consultas, no únicamente cuántos valores existen. [Elección de la columna](https://docs.citusdata.com/en/v13.0/sharding/data_modeling.html)

En mi ejemplo, consultar los movimientos de un cliente encaja con esta llave. Una consulta por fecha que abarque a todos los clientes tendrá que recorrer varios shards. Ese es el costo que acepto para mantener juntos los datos de cada cliente.

## 3. Cómo encuentra el shard correcto

La aplicación de esta demo envía SQL al **coordinador**. Este consulta los metadatos, localiza el shard y dirige la operación al worker que lo tiene. Si la consulta necesita datos de varios shards, reparte el trabajo y reúne los resultados. [Coordinador y workers](https://docs.citusdata.com/en/v13.0/get_started/concepts.html#coordinator-and-workers)

El mapa no es una lista escrita a mano en la aplicación. Citus mantiene, entre otros, estos catálogos:

| Catálogo | Información que aporta |
| --- | --- |
| `pg_dist_partition` | Tabla distribuida, método y columna de reparto |
| `pg_dist_shard` | Identificadores de shards e intervalos de hash |
| `pg_dist_placement` | Grupo de nodos donde está cada shard |
| `pg_dist_node` | Nombre, puerto y estado de los nodos |

La vista `citus_shards` permite consultar de forma más directa el shard y su ubicación. [Tablas y vistas de Citus](https://docs.citusdata.com/en/v13.0/develop/api_metadata.html)

La consulta `WHERE cliente_id = 7` produjo **una tarea** en la prueba. En cambio, sumar los importes de toda la tabla produjo **seis tareas**, una por shard. Esto muestra por qué incluir la llave permite evitar trabajo en particiones ajenas a la consulta.

## 4. Qué pasa al agregar capacidad y cuántos datos se mueven

Hay que distinguir **agregar un servidor** de **crear más shards**. Al registrar un nuevo worker con `citus_add_node`, los shards existentes conservan su ubicación. Para aprovechar el nuevo servidor se ejecuta el rebalanceo, que mueve shards y actualiza sus ubicaciones. [Administración del clúster](https://docs.citusdata.com/en/v13.0/admin_guide/cluster_management.html#scaling-the-cluster)

No existe un porcentaje fijo de filas que siempre se mueva: depende de los shards elegidos y de los datos que contengan. En esta ejecución había seis shards y dos workers; al agregar un tercero y equilibrar **por cantidad de shards**, se movieron dos shards. Esos dos contenían **12 de las 24 filas**, de modo que se trasladó un tercio de los shards, pero la mitad de las filas. Los valores de la llave siguieron perteneciendo al mismo shard; cambió el nodo que lo almacenaba.

Para aumentar el número de shards de una tabla existente, Citus ofrece operaciones como `alter_distributed_table(..., shard_count := ...)`. Eso modifica la partición y puede redistribuir filas; es una operación distinta de mover los seis shards existentes entre servidores. [Funciones de distribución](https://docs.citusdata.com/en/v13.0/develop/api_udf.html#alter-distributed-table)

## 5. Qué hace con un shard caliente

Un shard puede estar caliente porque recibe muchas consultas o concentra muchos datos. Citus permite mover shards, cambiar la estrategia de balanceo y **aislar un cliente grande** con `isolate_tenant_to_new_shard`. Después se puede mover su shard a otro worker para reducir la competencia con otros clientes. [Aislamiento de clientes](https://docs.citusdata.com/en/v13.0/admin_guide/cluster_management.html#tenant-isolation)

Equilibrar el número de shards no garantiza equilibrar la carga. En la prueba final cada worker terminó con dos shards, pero con **4, 8 y 12 filas**, respectivamente. Tampoco conté consultas por segundo, por lo que ese reparto no demuestra un equilibrio de tráfico.

Mi conclusión es que, si un solo cliente concentra casi todo el trabajo, moverlo puede proteger a los demás, pero no divide automáticamente su actividad entre varios servidores. Como todas sus filas usan la misma llave, habría que evaluar una llave más granular y el costo de separar sus datos. La documentación advierte que una distribución muy concentrada en pocos valores sobrecarga ciertos shards. [Criterios para elegir la llave](https://docs.citusdata.com/en/v13.0/sharding/data_modeling.html#real-time-apps)

## Opcional · Demostración real con Docker

Se ejecutó el **9 de octubre de 2026, de 23:36 a 23:37, hora de Ciudad de México**. Se usaron Citus **13.0.1** y PostgreSQL **17.2**. La imagen está fijada por versión y digest en [compose.yaml](compose.yaml). Se ejecuta en `linux/amd64`; en un equipo ARM utiliza la emulación de Docker.

El entorno empieza con un coordinador y dos workers. Los servicios se comunican en una red de Docker sin publicar puertos en el equipo. Cada worker tiene su propio volumen. Son procesos separados en la misma computadora: el experimento demuestra distribución y traslado, no rendimiento entre máquinas ni alta disponibilidad.

### Reproducir la prueba completa

Requiere Docker Compose y Python 3. Desde esta carpeta, con los volúmenes de esta demo vacíos:

```sh
python3 demo.py
```

[demo.py](demo.py) levanta los nodos, ejecuta [preparar.sql](preparar.sql), consulta cada tabla física, agrega el tercer worker, rebalancea y verifica que no falten ni se dupliquen filas. Guarda los comandos y sus salidas en `resultados/`, para conservar aparte la evidencia original entregada.

La preparación también se puede ejecutar directamente:

```sh
docker compose up -d --wait
docker compose exec -T coordinador psql -X -U postgres -d t03 \
  -v ON_ERROR_STOP=1 < preparar.sql
```

`preparar.sql` declara la llave y crea seis shards:

```sql
SELECT create_distributed_table(
    'movimientos', 'cliente_id',
    colocate_with => 'none', shard_count => 6
);
```

Después inserta dos movimientos para cada cliente del 1 al 12. Los importes se calculan como `cliente_id * 10 + movimiento_id`. La salida de la inserción fue:

```text
INSERT 0 24
```

### Dónde quedó cada dato antes del rebalanceo

La siguiente tabla reúne las **24 filas** leídas directamente de los workers. Cada renglón muestra los dos movimientos del cliente. Los datos completos están en [01-dos-workers.json](evidencia/01-dos-workers.json).

| Cliente | Movimiento 1: importe | Movimiento 2: importe | Shard | Worker inicial |
| --- | ---: | ---: | ---: | --- |
| 1 | 11.00 | 12.00 | 102008 | worker1 |
| 2 | 21.00 | 22.00 | 102012 | worker1 |
| 3 | 31.00 | 32.00 | 102010 | worker1 |
| 4 | 41.00 | 42.00 | 102009 | worker2 |
| 5 | 51.00 | 52.00 | 102009 | worker2 |
| 6 | 61.00 | 62.00 | 102011 | worker2 |
| 7 | 71.00 | 72.00 | 102009 | worker2 |
| 8 | 81.00 | 82.00 | 102008 | worker1 |
| 9 | 91.00 | 92.00 | 102013 | worker2 |
| 10 | 101.00 | 102.00 | 102008 | worker1 |
| 11 | 111.00 | 112.00 | 102013 | worker2 |
| 12 | 121.00 | 122.00 | 102013 | worker2 |

No se infirió la ubicación contando contenedores. El programa consultó `citus_shards`, calculó el shard esperado con `get_shard_id_for_distribution_column` y leyó cada tabla física en el worker correspondiente. Comparó sus filas con la consulta lógica y con los 24 registros insertados.

### Ruteo de una consulta

Comando ejecutado en el coordinador antes de agregar el tercer worker:

```sql
EXPLAIN (COSTS OFF)
SELECT * FROM movimientos WHERE cliente_id = 7;
```

Extracto de la salida real:

```text
Custom Scan (Citus Adaptive)
  Task Count: 1
  Tasks Shown: All
  ->  Task
        Node: host=worker2 port=5432 dbname=t03
        ->  Bitmap Heap Scan on movimientos_102009 movimientos
```

Para `EXPLAIN (COSTS OFF) SELECT sum(importe) FROM movimientos;`, el plan mostró `Task Count: 6`. Ambas salidas completas están en [ejecucion.txt](evidencia/ejecucion.txt).

### Agregar el tercer worker y rebalancear

Estos son los comandos de esa etapa; `demo.py` los ejecuta automáticamente:

```sh
docker compose --profile escala up -d --wait worker3
docker compose exec -T coordinador psql -X -U postgres -d t03 \
  -v ON_ERROR_STOP=1 -c "SELECT citus_add_node('worker3', 5432);"
docker compose exec -T coordinador psql -X -U postgres -d t03 \
  -v ON_ERROR_STOP=1 <<'SQL'
SELECT citus_rebalance_start(rebalance_strategy := 'by_shard_count');
SELECT citus_rebalance_wait();
SELECT state FROM citus_rebalance_status();
SQL
```

Después de registrar el nodo y antes de rebalancear, **worker3 tenía cero shards**. El programa comparó las ubicaciones y confirmó que seguían iguales. Después del rebalanceo quedaron así:

| Shard | Antes | Después | Filas del shard |
| ---: | --- | --- | ---: |
| 102008 | worker1 | worker3 | 6 |
| 102009 | worker2 | worker3 | 6 |
| 102010 | worker1 | worker1 | 2 |
| 102011 | worker2 | worker2 | 2 |
| 102012 | worker1 | worker1 | 2 |
| 102013 | worker2 | worker2 | 6 |

La cantidad de shards permaneció en seis. Se trasladaron **dos shards y 12 filas**. La suma de importes permaneció en **1596.00**, y la comparación completa de claves e importes confirmó las mismas 24 filas antes y después. Los resultados están en [02-nodo-agregado.json](evidencia/02-nodo-agregado.json), [03-rebalanceado.json](evidencia/03-rebalanceado.json) y [resumen.json](evidencia/resumen.json).

Para comprobar directamente dónde quedó el cliente 7 después del traslado:

```sh
docker compose exec -T worker3 psql -X -U postgres -d t03 -c \
  "SELECT * FROM movimientos_102009 WHERE cliente_id=7 ORDER BY movimiento_id;"
```

Salida real:

```text
 cliente_id | movimiento_id | importe
------------+---------------+---------
          7 |             1 |   71.00
          7 |             2 |   72.00
(2 rows)
```

El ID del shard puede cambiar en otra instalación. Para localizarlo se debe consultar el mapa, no fijar `102009` en la aplicación. [consultas-finales.txt](evidencia/consultas-finales.txt) conserva esta comprobación y los totales finales.

### Cerrar o repetir

Para apagar únicamente esta demo y conservar los datos:

```sh
docker compose --profile escala down
```

Para **repetir desde cero**, el siguiente comando borra solo los volúmenes de este proyecto de demostración. Después se usa una carpeta de salida nueva:

```sh
docker compose --profile escala down -v
python3 demo.py --salida resultados/repeticion-2
```

El experimento verifica almacenamiento, ruteo y conservación de datos durante el traslado. No se generó tráfico concurrente para medir un shard caliente: esa parte se explica con las fuentes, no se presenta como una medición.

## Fuentes

- Citus 13.0. [Conceptos: coordinador, workers y modalidades de sharding](https://docs.citusdata.com/en/v13.0/get_started/concepts.html).
- Citus 13.0. [Elección de la columna de distribución](https://docs.citusdata.com/en/v13.0/sharding/data_modeling.html).
- Citus 13.0. [Tablas y vistas de metadatos](https://docs.citusdata.com/en/v13.0/develop/api_metadata.html).
- Citus 13.0. [Administración del clúster: expansión, rebalanceo y aislamiento](https://docs.citusdata.com/en/v13.0/admin_guide/cluster_management.html).
- Citus 13.0. [Funciones: distribución, cambio del número de shards y rebalanceo](https://docs.citusdata.com/en/v13.0/develop/api_udf.html).
- Citus Data. [Repositorio oficial de las imágenes Docker](https://github.com/citusdata/docker).

Fuentes consultadas el 9 de octubre de 2026. Trabajo preparado con apoyo de Codex; los resultados de la demostración proceden de la ejecución local y se conservan completos en `evidencia/`.
