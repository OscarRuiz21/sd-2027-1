Una implementación real de sharding.

Amazon DynamoDB.

1. ¿Qué es y para qué se usa DynamoDB?

DynamoDB es un servicio de base de datos de Amazon de tipo NoSQL, es decir, que no sigue el modelo relacional tradicional, en el cual la información se divide en varias tablas conectadas entre sí. En cambio, reúne en un solo registro toda la información relacionada, por ejemplo los datos del cliente, los productos y el total de un pedido, en lugar de repartirlos en tablas distintas. Esto le permite responder de forma más eficiente a las solicitudes que recibe por parte de la aplicación. Una característica fundamental de DynamoDB es que AWS se encarga de su mantenimiento, es decir, de instalar, actualizar y administrar los servidores, por lo que quien lo utiliza no tiene que ocuparse de esas tareas. Además, solo se paga por lo que se utiliza, sin necesidad de adquirir infraestructura adicional. Una limitación es que no permite combinar información de varias tablas en una sola consulta, por lo que los datos deben organizarse desde el inicio de manera que todo lo necesario esté reunido en un solo lugar.

El uso de DynamoDB radica principalmente en la necesidad de un funcionamiento constante y veloz, sin importar cuánto crezca la cantidad de usuarios, como ocurre en el carrito de compras de una tienda en línea, que debe seguir respondiendo rápido tanto en un día normal como en uno de mucha demanda. También es útil para proyectos que comienzan con pocos recursos y que pueden crecer a nivel mundial. Como se menciona en la página oficial de AWS, Amazon emplea DynamoDB en sus propios servicios, como Alexa, su tienda en línea y sus centros logísticos, incluso en fechas de mucho tráfico como Prime Day.

2. Cómo reparte los datos: ¿por rango, por hash o de otra forma? ¿Quién elige la llave?

DynamoDB reparte los datos por hash. Esto significa que toma el valor de la clave de partición de cada elemento y le aplica un cálculo, y el resultado de ese cálculo indica en qué partición se guardará. Si la tabla tiene además una clave de ordenación, los elementos que comparten la misma clave de partición quedan juntos y ordenados, pero a qué partición va cada grupo lo sigue decidiendo el cálculo.

Como se mencionaba indirectamente en las fuentes de mi consulta las clave la elige quien diseña la tabla, es decir, el desarrollador y DynamoDB se encarga de administrar las particiones. Lo recomendable es escoger un dato que sea distinto en cada elemento, como el correo electrónico o el código de cliente, para que de esta forma la información se reparta de forma equitativa y ninguna partición llegue a saturarse. 

3. Cómo encuentra el shard correcto cuando llega una consulta: ¿quién guarda el mapa de qué dato vive dónde?

Cuando llega a una consulta, DynamoDB toma la clave de partición que se le indicay hace un calculo con ella, lo que explicamos anteriormente como hash. El resultado le dice en qué parte está guardado el dato. Si la tabla usa también clave de ordenación, dentro de esa parte busca el dato exacto con ella.

El mapa lo guarda el servicio de metadatos, que forma parte de la arquitectura interna de DynamoDB junto con los enrutadores de solicitudes y los nodos de almacenamiento. De esta forma los enrutadores consultan ese servicio para saber a qué nodo enviar la solicitud, y guardan en caché la información de ruteo para no consultarla cada vez.

4. Qué pasa cuando agregas un shard: ¿se mueven los datos? ¿cuántos?

En DynamoDB no se agrega un shard a mano, porque el servicio crea particiones nuevas automáticamente cuando una partición se llena o cuando se necesita más rendimiento. La forma para hacerlo es dividiendo la partición en particiones hijas, de esta manera podríamos a su vez mover datos, pero solo de aqueas particiones que anteriormente han sido segmentadas. Cuando la división es por exceso de tráfico, DynamoDB no la corta por la mitad, sino que observa qué llaves ha recibido esa partición y corta en el punto que deje a las dos nuevas con una carga más parecida para evitar saturación de los segmentos.

En un aproximado la mitad de los datos de la partición que se divide se mueven, mientras que el resto de la tabla no se mueve. Esta proporción es aproximada, porque cuando la división es por exceso de tráfico, el corte no siempre queda exactamente a la mitad.  

5. Qué hace con un shard caliente, si hace algo. 

Una partición caliente se caracteriza por recibir mucho más tráfico de lo convencional. Si no se atiende, las solicitudes se frenan y las respuestas se vuelven más lentas. Para evitarlo, DynamoDB le presta capacidad que otras particiones no están usando y, si el tráfico sigue alto, la divide en dos para repartir la carga. 

Pero dentro de esto existe un límite si todo el tráfico va a un solo dato, dividir la partición no sirve. En ese caso el desarrollador debe elegir una mejor clave de partición o usar una caché, como DAX, que guarda los datos más consultados.

Fuentes de investigación.

[1] Amazon Web Services, “¿Qué es Amazon DynamoDB?,” *AWS Documentation*. [En línea]. Disponible en: https://docs.aws.amazon.com/es_es/amazondynamodb/latest/developerguide/Introduction.html. [Accedido: 9-oct-2026].

[2] Amazon Web Services, “Particiones y distribución de datos en DynamoDB,” *AWS Documentation*. [En línea]. Disponible en: https://docs.aws.amazon.com/es_es/amazondynamodb/latest/developerguide/HowItWorks.Partitions.html. [Accedido: 9-oct-2026].

[3] Amazon Web Services, “Choosing the Right DynamoDB Partition Key,” *AWS Blog*. [En línea]. Disponible en: https://aws.amazon.com/es/blogs/aws-spanish/choosing-the-right-dynamodb-partition-key/. [Accedido: 9-oct-2026].

[4] Amazon Web Services, “Particiones y distribución de datos en DynamoDB,” *AWS Documentation*. [En línea]. Disponible en: https://docs.aws.amazon.com/es_es/amazondynamodb/latest/developerguide/HowItWorks.Partitions.html. [Accedido: 9-oct-2026].

[5] ScyllaDB, “DynamoDB Hot Partition,” *ScyllaDB Glossary*. [En línea]. Disponible en: https://www.scylladb.com/glossary/dynamodb-hot-partition/. [Accedido: 9-oct-2026].
