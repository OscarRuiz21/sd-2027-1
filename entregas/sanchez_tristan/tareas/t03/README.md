## TAREA 3: SHARDING
**Alumno:** Sánchez Mayén Tristán Qesen

---

La tecnologia que seleccione que tiene incluido Sharding es Citus (extension de PostgreSQL).

**- 1.- Qué es y para qué se usa.**
Como Citus es una extension de postgres no es un sistema nuevo o empezado desde cero. Lo que hace Citrus es transformar el servidor de una base de datos, de un nodo a una base de datos distribuida. Asi es como se cumple el concepto de Sharding que es escalar horizontalmente para distribuir los datos.

**- 2.- Cómo reparte los datos: ¿por rango, por hash o de otra forma? ¿Quién elige la llave?**
Citus utiliza un método hibrido para repartir los datos, puede ser por rango o por hash. Quien elige la llave es el desarrollador, el puede decidir que columna sera la llave de sharding. 
Una vez que ya se tenga elegida la llave, Citus tomara el valor de la columna seleccionada, le aplicara un hash y segun el resultado va a enviarla a uno de los shards logicos que estan en los nodos trabajadores.  

**- 3.- Cómo encuentra el shard correcto cuando llega una consulta: ¿quién guarda el mapa de qué dato vive dónde?**
Citus maneja dos roles para los nodos, uno es el trabajador (worker) y el otro es el coordinador. El nodo coordinados es el que tiene el mapa de que dato vive donde y esta informacion se guarda en una tabla de metadatos.
Entonces, el proceso de la llegada dde un dato es: llega al nodo coordinador, el noso coordinador inspecciona la consulta y extrae la llave de particion, luego revisa sus metadatos para saber en que nodo trabajador debe de ir.

**- 4.- Qué pasa cuando agregas un shard: ¿se mueven los datos? ¿cuántos?**
Los datos no se mueven solos de forma automática. El nodo entra vacío y para balancear la carga, el desarrollador debe ejecutar una función llamada rebalance_table_shards(). Despues Citus calculara cuánto peso tiene cada nodo y movera un porcentaje de los shards lógicos de los Workers más llenos al nuevo Worker.

**- 5.- Qué hace con un shard caliente, si hace algo.**
Como tal Citus no resuelve los problemas con los Shards calientes, pero tiene algunas herramientas para administrarlo. Una de ellas es la vista de citus_stat_tenants para detectar llaves que consumen muchos recursos. La otra es que permite hacer Tenant Isolation, asi si un cliente específico es el que satura el sistema, puedes usar la función isolate_tenant_to_new_shard() para mover únicamente a ese cliente pesado a su propio nodo dedicado, liberando así al resto de los shards.

---

## ACTIVIDAD EXTRA
Para levantar los contenedores:
```bash
docker compose up -d
```
Para entrar al coordinador:
```bash
docker exec -it t03_coordinador_1 psql -U postgres
```

Una vez dentro del coordinador, debemos ejecutar los siguientes comandos:
```sql
-- 1. Le decimos al coordinador quiénes son sus workers
SELECT citus_set_coordinator_host('coordinador', 5432);
SELECT citus_add_node('worker1', 5432);
SELECT citus_add_node('worker2', 5432);

-- 2. Creamos una tabla normal
CREATE TABLE proyectos (
    id_proyecto serial,
    nombre text,
    desarrollador text
);

-- 3. ¡La magia de Citus! Convertimos la tabla en distribuida usando id_proyecto como llave de hash
SELECT create_distributed_table('proyectos', 'id_proyecto');

-- 4. Insertamos datos de prueba
INSERT INTO proyectos (nombre, desarrollador) VALUES
('Motor Gráfico C++ con OpenGL', 'Tristan'),
('App DOCTU en React', 'Darinka'),
('Arquitectura REST/gRPC', 'Amir'),
('Redes GNS3 y Cisco', 'Tristan');
```


Para ver a qué shard ID lógico se fue una fila en particular mediante la llave de partición:
```sql
SELECT get_shard_id_for_distribution_column('proyectos', id_proyecto);
```

Por ejemplo, yo busque el id_proyecto = 3 y el resultado fue:
```bash
postgres=# SELECT get_shard_id_for_distribution_column('proyectos', 3);
 get_shard_id_for_distribution_column 
--------------------------------------
                               102023
```


Para ver en qué Worker físico vive cada pedazo de la tabla y cuánto pesa, podemos ejecutar el siguiente comando:
```sql
SELECT nodename, shardid, shard_size 
FROM citus_shards 
WHERE table_name = 'proyectos'::regclass;
```

El resultado de ejecutar esa consulta (en mi caso) es el siguiente:
```bash
 nodename | shardid | shard_size 
----------+---------+------------
 worker1  |  102008 |       8192
 worker2  |  102009 |      16384
 worker1  |  102010 |       8192
 worker2  |  102011 |       8192
 worker1  |  102012 |       8192
 worker2  |  102013 |       8192
 worker1  |  102014 |       8192
 worker2  |  102015 |       8192
 worker1  |  102016 |      16384
...
```


**Fuentes:**
- Documentación oficial de Citus Data: Architecture & Concepts (citusdata.com)
- Repositorio oficial de Citus en GitHub (github.com/citusdata/citus)