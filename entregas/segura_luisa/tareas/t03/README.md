\# T03 - Una implementación real de sharding: MongoDB



\## 1. Qué es y para qué se usa

MongoDB es un sistema de gestión de bases de datos no relacional de código abierto que guarda la información en forma de documentos flexibles con formato JSON binario (BSON), en lugar de usar tablas rígidas con filas y columnas. Se utiliza principalmente cuando se necesitan construir aplicaciones con alta velocidad, disponibilidad y rendimiento, capaces de manejar tipos de datos variados. 



El \*sharding\* (o particionado) se usa para distribuir los documentos de una colección entre varios servidores o particiones cuando los datos crecen demasiado (por ejemplo, más de 3TB) o cuando la colección sobrepasa la memoria RAM de una sola máquina, lo que provocaría que las consultas se vuelvan lentas por tener que leer constantemente del disco.



\## 2. Cómo reparte los datos

Para repartir la información, MongoDB utiliza una \*\*Shard Key\*\* (clave de partición), la cual consiste en un campo o varios campos de los documentos que cuentan con un índice. Existen principalmente dos formas de repartir estos datos: 



\* \*\*Por Rango (Particionamiento clasificado por rango):\*\* Usa los campos seleccionados para agrupar documentos con valores similares en la misma partición. Es ideal si en la aplicación se suele hacer consultas que buscan rangos de datos. 

\* \*\*Por Hash (Particionamiento encriptado/hashed):\*\* Calcula un valor matemático (\*hash\*) a partir del campo especificado y distribuye los documentos de forma aleatoria por todo el clúster. Esto ayuda a escalar y repartir mejor las operaciones de escritura. \*(También existe el particionado por zonas para colocar datos en un grupo específico de particiones por cercanía geográfica o normas)\*. 



\*\*¿Quién elige la llave?\*\* La elige el desarrollador o administrador al momento de fragmentar la colección mediante comandos como `sh.shardCollection()`, especificando el espacio de nombres de la colección y los campos de la clave. Se pueden usar herramientas del sistema como `analyzeShardKey` para ayudar a seleccionar una clave efectiva que distribuya bien la carga.



\## 3. Cómo encuentra el shard correcto cuando llega una consulta

MongoDB divide los valores de la clave de partición en rangos que no se superponen (asociados a fragmentos o \*chunks\*), y busca mantenerlos distribuidos de forma uniforme entre las particiones. 



Cuando llega una consulta, el sistema revisa la clave de partición que se especificó para determinar exactamente a qué partición pertenece el documento buscado. Si la consulta se diseñó aprovechando la clave de partición, la búsqueda va directo a la partición correspondiente donde cada una mantiene sus propios índices, lo que acelera el tiempo de respuesta.



\## 4. Qué pasa cuando agregas un shard: ¿se mueven los datos? ¿cuántos?

\*\*Sí, los datos se redistribuyen.\*\*



MongoDB cuenta con un proceso automático llamado \*\*balanceador de clúster\*\* (\*cluster balancer\*). Al modificar la estructura del clúster o fragmentar una colección, el balanceador realiza una migración automática de los datos o una redistribución para asegurar que el trabajo y la cantidad de documentos queden repartidos de manera uniforme entre todas las particiones disponibles. Solo se mueven los fragmentos necesarios hasta equilibrar la carga.



\## 5. Qué hace con un shard caliente

Un \*shard caliente\* ocurre cuando la elección de la clave de partición no fue adecuada, provocando una distribución desigual de la carga, la generación de bloques gigantescos (\*jumbo chunks\*) o la caída en el rendimiento de las consultas. 



Para resolver esto, MongoDB permite dos acciones para corregir la clave:

1\. \*\*Refinar la clave de partición:\*\* Se le pueden añadir campos a la clave que ya existe para volverla más específica.

2\. \*\*Refragmentar la colección:\*\* Se puede cambiar por completo la clave de partición antigua y volver a distribuir la colección bajo la nueva llave para corregir el desequilibrio.



\---



\## Fuentes consultadas

\* Claves de partición. (s. f.). \*Shard Keys\*. Recuperado 7 de octubre de 2026, de https://www.mongodb.com/es/docs/manual/core/sharding-shard-key/ 

\* Distribuir datos de la colección. (s. f.). \*MongoDB\*. Recuperado 7 de octubre de 2026, de https://www.mongodb.com/es/docs/manual/core/sharding-distribute-collection-data/

\* ¿Qué es MongoDB? (2024, 7 octubre). \*IBM\*. Recuperado 7 de octubre de 2026, de https://www.ibm.com/mx-es/think/topics/mongodb

