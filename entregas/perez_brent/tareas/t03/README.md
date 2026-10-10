# T03 · Una implementación real de sharding: MongoDB

### 1. Qué es y para qué se usa
**MongoDB** es una base de datos NoSQL orientada a documentos. Utiliza el *sharding* para **escalar horizontalmente**. Esto significa que cuando la BD crece tanto que un solo servidor ya no aguanta el volumen de datos, la cantidad de consultas o se queda sin recursos, MongoDB divide la información en partes más pequeñas (*shards*) y las distribuye en múltiples servidores. Al final, logra que un grupo de varios servidores trabaje en conjunto como si fuera una sola BD gigante.

### 2. Cómo reparte los datos
MongoDB soporta principalmente dos formas de repartir la información:
* **Por rango (Ranged Sharding):** Agrupa los datos basándose en valores secuenciales o continuos (por ejemplo, clientes del 1 al 1000 van al Shard A, del 1001 al 2000 al Shard B). Es excelente si se hacen consultas por rangos, pero puede crear cuellos de botella.
* **Por Hash (Hashed Sharding):** Toma la llave de partición, la pasa por una función matemática (hash) y distribuye los datos de forma aleatoria y uniforme. Es ideal para evitar que un solo servidor reciba todo el tráfico de golpe.

**¿Quién elige la llave?** 
El desarrollador o administrador de la base de datos. Es quien debe analizar cómo se consultan los datos en la aplicación y elegir explícitamente qué campo servirá como llave de partición (*Shard Key*).

### 3. Cómo encuentra el shard correcto
En MongoDB, la aplicación nunca se conecta directamente a los servidores donde están los datos. Se conecta a un enrutador especial llamado **`mongos`**.

**¿Quién guarda el mapa?** 
Unos servidores dedicados llamados **Config Servers**. Ellos guardan los metadatos y el "índice" que dice exactamente en qué shard vive cada rango de datos (*chunks*). Cuando llega una consulta, el **`mongos`** le pregunta a los Config Servers dónde está la información, y luego redirige la petición al shard correcto.

### 4. Qué pasa cuando agregas un shard
Cuando añades un nuevo servidor (shard) a tu clúster, los datos sí se mueven. MongoDB cuenta con un proceso automático en segundo plano llamado **Balancer**. 

El Balancer detecta que hay un nuevo shard vacío y comienza a migrar fragmentos de datos (*chunks*) desde los shards más llenos hacia el nuevo. No se mueve todo, solo se transfiere la cantidad de *chunks* necesaria hasta que todos los servidores queden nivelados y tengan una cantidad equitativa de información. Todo esto ocurre en vivo, sin detener la BD.

### 5. Qué hace con un shard caliente
Un *hot shard* ocurre cuando todo el tráfico de escritura o lectura golpea a un solo servidor, ignorando a los demás (por ejemplo, si la llave es la fecha de hoy, todos los registros nuevos se irán al mismo shard). 

Si esto pasa, el *Balancer* intentará mover pedazos de datos si detecta un desequilibrio de almacenamiento, pero no está diseñado para resolver la saturación de tráfico instantáneo. La verdadera forma en que MongoDB lo resuelve o que lo previene es forzándo a uno a aplicar un buen diseño desde el principio: permite usar el **Hashed Sharding** (para que las escrituras se repartan parejo) o usar una **Llave Compuesta** (combinando dos o más campos para romper la secuencialidad).

---

### Fuentes utilizadas
*   **Sistemas Distribuidos**: Documento de referencia sobre conceptos de infraestructura y escalamiento horizontal.
*   **Documentación oficial de MongoDB**: Artículos sobre arquitectura de Sharding (*Mongos*, *Config Servers*, y el *Balancer*).
*   **MongoDB Sharding Strategies**: Guías de diseño de partición y elección de llaves para aplicaciones empresariales.
