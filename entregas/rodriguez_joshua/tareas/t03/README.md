# ***1.	¿Qué es MongoDB y para qué se usa?***
Es un sistema de gestión de bases de datos (DBMS) no relacional de código abierto que emplea documentos flexibles similares a JSON en lugar de tablas y filas para procesar y almacenar diversas formas de datos. Sus datos se almacenan con campos altamente personalizables y valores correspondientes. Es conocida por ser intuitiva, de fácil uso y escalable. 
Es ideal para manejar grandes cantidades de datos no estructurados o semiestructurados debido a que es una base de datos NoSQL y con ello, también podemos agregar las bases de datos distribuidas, guardando información y copias de estas en distintos servidores que pueden ser remotos o locales. 

# ***2.Cómo reparte los datos: ¿por rango, por hash o de otra forma? ¿Quién elige la llave?***
Se puede realizar por ambas formas, tanto por hash como por rango. MongoDB reparte la información similar dividiéndola en bloques de nombre chunks y distribuyéndolos por varias instancias de mongodb (shards) usando una clave de particionamiento, la cual es única y definida por un campo elegido por nosotros. Esta clave o shard key define como se distribuirá y se guardará la información mediante chunks en las diferentes shards.
Una buena clave de partición debe de estar basada en un campo con gran número de valores diferentes. Los datos ideales para las shard keys tienen campos que cambian monótonamente (como los id’s). Además, deben de tener una baja propabilidad de ocurrencia, es decir, que no se repitan.

### **Sharding por hash:** 
Para hacer el sharding, se toma el valor(es) de la shard key de un documento y se aplica una función de hashing que calcula y devuelve un número que determina a que chunk deberá mandarse, sin embargo, al ser aleatorio, los valores de hash son improbables a compartir el mismo chunk, por lo que la distribución será uniforme, sin importar si es secuencial.

### **Sharding por rango:**
Divide la información en rangos basados en el valor de su shard key, con cada chunk asignado para guardar los datos dentro de un rango definido. 
Un rango de shard keys cuyos valores están cercanos son más propensos a vivir en el mismo chunk, esto permite que las búsquedas objetivas pueden mapear las operaciones a las shards que contienen los datos requeridos.
La eficiencia de este método depende de la shard key elegida, una shard key mal elegida puede generar distribución no uniforme de datos, lo que puede negar beneficios de la fragmentación o causar cuellos de botella. 

# ***3.	¿Cómo encuentra el shard correcto cuando llega una consulta: ¿quién guarda el mapa de qué dato vive dónde?***
En el caso de MongoDB, el encargado de distribuir y guardar el registro de donde se encuentran los datos son las instancias de “mongos, una interfaz entre las aplicaciones de cliente y el clúster particionado, estas instancias encuentran qué datos están en cada partición revisando y almacenando en caché los metadatos de los servidores de configuración sólo para después guardarlos en la RAM para ser más rápido en las consultas.
Estos config servers son los encargados de almacenar la metadata de un clúster particionado, reflejando el estado y la organización de todos los datos y componentes dentro del clúster. La metadata es tan importante porque incluye la lista de chunks en cada shard y los rangos que definen a esos chunks. Entonces, sería valido decir que los config servers son los que guardan el mapa de dónde vive cada dato.
Por otro lado, las instancias de mongos funcionan como un router, dirigiendo cada consulta entre el cliente y el servidor hacia el Daemon mongod, que se encarga de manejar las peticiones de datos, manejar el acceso a los datos y realizar de fondo algunas operaciones de gestión. Estas no tienen un sistema persistente y consumen muy pocos recursos.

# ***4.	Qué pasa cuando agregas un shard: ¿se mueven los datos? ¿cuántos?***
Al agregar un shard al sistema, se afecta el balance de chunks dentro de las shards de un clúster. Por lo que el balanceador empezará a migrar chunks para que se alcance el balance, iniciando un proceso de escalado horizontal. Es decir, añadir este nuevo shard y dividir la carga de trabajo entre todo el sistema.
Al añadir un nuevo shard, el enrutador mongos detecta la nueva shard y lo revisa con el config server, y al visualizar que este no está siendo utilizado o que no tiene metadatos asignados, lo pone a disposición para recibir datos.

Es aquí cuando entra el balanceador y empieza a redistribuir la colección de datos en las nuevas shards del clúster. En este proceso sólo un shard puede participar en la migración de chunks a la vez y aunque puede ser un proceso paralelo entre diferentes shards a la vez, estos trabajan uno a la vez.
Para un clúster particionado con n shards, MongoDB puede realizar como máximo n/2 (redondeado abajo) migraciones simultaneas. 
En el momento en que MongoDB termina de copiar el rango de datos de una shard exitosamente a otra se actualiza el config server para que mongos sepa la nueva ubicación de los datos, y a su vez, el rango en la shard donante es marcada para su eliminación por el borrador de rangos. Proceso el cual, es lento y requiere de muchos recursos.

# ***5.	Qué hace con un shard caliente, si hace algo.***
Una shard caliente ocurre en MongoDB cuando una shard recibe una cantidad desproporcionada de escrituras y lecturas comparado a otras shards del clúster. Este cuello de botella limita todo el clúster y puede causar picos de latencia, saturación de la CPU y presión en el disco de I/O en la shard afectada.
Normalmente son causadas por una mala selección de una Shard Key, con llaves de baja cardinalidad (pocos valores posibles) o patrones de escritura monotónicas que generan pocos chunks. 
En toda la documentación de MongoDB no encontré un apartado que se refiriera como tal a las hot shards, más bien, se referían a problemas que son síntomas de una hot shard. Estas son chunks enormes o la distribución desigual en los shards. Simplemente se recomienda al administrador de las particiones que se busque cambiar el campo de las llaves o shard keys y procurar no utilizar campos monotónicos y de poca frecuencia. Con esto se busca que los datos no se vayan a una sola shard y evitar el problema de tráfico excesivo en una shard aislada.   






# ***Bibliografía***

* ¿Qué es MongoDB? (2026, June 1). IBM. https://www.ibm.com/mx-es/think/topics/mongodb 
* ¿Qué es MongoDB? | Google Cloud. (n.d.). Google Cloud. https://cloud.google.com/discover/what-is-mongodb?hl=es-419
* Sharding - Database Manual - MongoDB Docs. (n.d.). https://www.mongodb.com/docs/manual/sharding/ 
* Hashed Sharding - Database Manual - MongoDB Docs. (n.d.). https://www.mongodb.com/es/docs/manual/core/hashed-sharding/ 
* Diseño para todys. (2023, June 10). Sharding en MongoDB [Video]. YouTube. https://www.youtube.com/watch?v=xO5RvbzS87s 
* Interview Mentor App. (2026, March 9). Claves de Shard por Rango vs Hash en MongoDB [Video]. YouTube. https://www.youtube.com/watch?v=5sPSrAgGoNk 
* Routing with mongos - Database Manual - MongoDB Docs. (n.d.). https://www.mongodb.com/docs/manual/core/sharded-cluster-query-router/ 
 * Config Servers - Database Manual - MongoDB Docs. (n.d.). https://www.mongodb.com/docs/manual/core/sharded-cluster-config-servers/#std-label-sharded-cluster-config-server 
* Sharded Cluster Balancer - Database Manual - MongoDB Docs. (n.d.). https://www.mongodb.com/docs/manual/core/sharding-balancer-administration/#std-label-sharding-internals-balancing 
* Add shards to a cluster - database manual - MongoDB docs. (n.d.). https://www.mongodb.com/docs/manual/tutorial/add-shards-to-shard-cluster/ 
* Troubleshoot Shard Keys - Database Manual - MongoDB Docs. (n.d.). https://www.mongodb.com/docs/manual/core/sharding-troubleshooting-shard-keys/ 


