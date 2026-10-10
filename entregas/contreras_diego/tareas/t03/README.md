CoackroachDB



¿Qué es y para qué se usa?



Es un sistema de base de datos distribuidas, se usa para desarrollar e implementar aplicaciones en donde requieren alta disponibilidad continua, consistencia por medio de las transacciones ACID completas y una capacidad de escalabilidad automática.



Cómo reparte los datos: ¿por rango, por hash o de otra forma? ¿Quién elige la llave?



El reparto de datos se hace por rango. Se organiza todo el espacio de almacenamiento de la base de datos en un mapa ordenado de clave y valor, el cual se fragmenta de manera contigua en bloques. Para la elección de la llave CockroachDB genera internamente claves únicas combinando el identificador de la tabla, identificador del índice y los valores pertenecientes a la clave primaria definida por el usuario.



Cómo encuentra el shard correcto cuando llega una consulta: ¿quién guarda el mapa de qué dato vive dónde?



CockroachDB usa un catálogo de ubicación guardado en una estructura de tres niveles. El primer nivel contiene la dirección general del catálogo y se comparte entre todas las computadoras del sistema mediante un protocolo de comunicación continuo. El segundo nivel guarda un mapa detallado que dice exactamente en qué computadora física vive cada bloque de información. Cuando se hace una consulta, la computadora que recibe la orden no necesita buscar a ciegas ni revisar todo el disco. Primero revisa su registro de apunte rápido en memoria. Si no tiene la dirección anotada, consulta el catálogo general en dos pasos rápidos hasta saber a qué computadora exacta debe pedirle la información solicitada.



Qué pasa cuando agregas un shard: ¿se mueven los datos? ¿cuántos?



Cuando un bloque de datos se llena demasiado y sobrepasa su límite de capacidad, la base de datos realiza automáticamente un corte por la mitad. Este corte ocurre de forma transparente, sin pausar el sistema ni afectar a los usuarios que están conectados. En este movimiento no se reorganiza toda la base de datos entera ni se recalculan todos los archivos. Solamente se toma el bloque que se llenó y se parte en dos fragmentos pequeños de aproximadamente la mitad de su tamaño original. Después, el sistema revisa si es necesario enviar uno de esos dos fragmentos a otra computadora que tenga más espacio libre o esté menos ocupada.



Qué hace con un shard caliente, si hace algo.

Un bloque caliente es aquel que recibe muchísimas peticiones al mismo tiempo, por ejemplo cuando un producto en una tienda en línea se vuelve muy popular. Para resolver este problema el sistema corta automáticamente ese bloque en partes más pequeñas para aislar los datos más pedidos. De manera inmediata, pasa el trabajo de atender esas peticiones a una computadora con el procesador más despejado o mueve copias del bloque a otros nodos menos ocupados para repartir el esfuerzo.



\[1] O. Babatunde, M. K. C. S. Aiyer y A. S. L. A. S., "Capítulo 1", en CockroachDB: La Guía Definitiva, O'Reilly Media, 2022. Disponible en: https://www.oreilly.com/library/view/cockroachdb-la-guia/9781098197445/ch01.html



\[2] New Relic, "Integración de CockroachDB - Documentación de New Relic", docs.newrelic.com, 2024. \[En línea]. Disponible: https://docs.newrelic.com/es/docs/infrastructure/prometheus-integrations/integrations-list/cockroach-db-integration/. \[Accedido: 09-oct-2026].



\[3] Euroinnova International Online Education, "¿Qué es el sharding de una base de datos y cómo funciona?", tecnologia.euroinnova.com, 2023. \[En línea]. Disponible: https://tecnologia.euroinnova.com/sharding. \[Accedido: 09-oct-2026].



\[4] Universidad Internacional de La Rioja (UNIR), "¿Qué es el sharding, cómo funciona y para qué se usa?", unir.net/revista/ingenieria, nov. 2022. \[En línea]. Disponible: https://www.unir.net/revista/ingenieria/sharding/. \[Accedido: 09-oct-2026].

