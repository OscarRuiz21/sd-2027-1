# Sistemas Distribuidos - Tarea 04
**¿Qué reloj usan las tecnologías que conoces?**

**Alumna:** Sierra García Mariana  
**Grupo:** 02  
**Profesor:** Oscar Manuel Ruiz Hurtado  
**Institución:** Universidad Nacional Autónoma de México (UNAM) - Facultad de Ingeniería  

---

## 1. Google Cloud Spanner

### ¿Qué es y para qué se usa?
Es una base de datos relacional global, distribuida y completamente administrada. Logra combinar la estructura de esquemas relacionales y SQL con alta disponibilidad y escalabilidad horizontal. Se utiliza para aplicaciones críticas a nivel mundial que requieren transacciones ACID distribuidas y lecturas/escrituras consistentes sin perder la integridad transaccional.

### Mecanismo de tiempo para el ordenamiento
Usa un modelo híbrido llamado **TrueTime API**, combinando relojes físicos atómicos de cesio y receptores GPS en cada centro de datos, teniendo en cuenta que ningún reloj físico es exacto. TrueTime entrega el tiempo como un intervalo $[t_{earliest}, t_{latest}]$ con un margen de incertidumbre acotado $e$.

### Garantía y alcance del ordenamiento
Garantiza **consistencia externa (Linearizabilidad)** en todo el sistema. Si una transacción $T_2$ se inicia en tiempo real después de que $T_1$ se ha confirmado, la marca de tiempo ($s_2$) asignada a $T_2$ será estrictamente mayor que la marca de tiempo ($s_1$) de $T_1$.

### Gestión de desincronización de relojes
Para esta parte se aplica la técnica **Commit Wait**:
1. Asigna como marca de tiempo de commit el límite superior del intervalo actual: $s = t_{latest}$.
2. El nodo espera deliberadamente sin finalizar el commit hasta que el reloj local asegure que $t_{earliest} > s$.
3. Esta pausa de duración $2e$ garantiza que ninguna transacción posterior obtenga una marca de tiempo menor o igual.

---

## 2. Apache Kafka

### ¿Qué es y para qué se usa?
Es una plataforma distribuida de transmisión de eventos de alto rendimiento, la cual se utiliza para publicar, suscribirse, almacenar y procesar flujos de eventos en tiempo real a gran escala.

### Mecanismo de tiempo para el ordenamiento
Utiliza un número de secuencia asignado por el broker denominado **Offset**, y aunque los eventos pueden incluir marcas de tiempo físicas, el orden determinista de procesamiento se basa estrictamente en el offset dentro de la partición.

### Garantía y alcance del ordenamiento
Garantiza orden estricto **únicamente dentro de una misma partición** de un tópico. Esto no garantiza orden global a nivel de todo el sistema ni entre distintas particiones del mismo tópico.

### Gestión de desincronización de relojes
Dado que el orden de los mensajes se determina secuencialmente mediante el offset asignado en la partición, la desincronización de relojes físicos entre clientes o servidores no afecta ni altera el orden de entrega de los mensajes dentro de la partición.

---

## 3. RabbitMQ

### ¿Qué es y para qué se usa?
Es un intermediario de mensajes ligero y de alto rendimiento que soporta múltiples protocolos de mensajería y se utiliza para desacoplar aplicaciones, distribuir cargas de trabajo asíncronas y gestionar colas de tareas entre microservicios.

### Mecanismo de tiempo para el ordenamiento
Utiliza un número de secuencia interno asignado de forma **FIFO** por el nodo donde reside la cola. El orden de los mensajes está determinado estrictamente por la secuencia de llegada e inserción en la cola.

### Garantía y alcance del ordenamiento
Garantiza **orden FIFO únicamente dentro de una misma cola** y para mensajes consumidos de forma secuencial por un solo canal o consumidor. No garantiza orden entre colas distintas ni cuando existen múltiples consumidores en paralelo sobre la misma cola sin un ordenamiento estricto a nivel de aplicación.

### Gestión de desincronización de relojes
El ordenamiento no depende del tiempo del reloj físico de los emisores ni del servidor, por lo que los desajustes en el reloj se ignoran para el enrutamiento y la secuencia de entrega dentro de las colas.

---

## Referencias

1. Corbett, J. C., et al. (2012). *Spanner: Google's Globally-Distributed Database*. En Proceedings of the 10th USENIX Symposium on Operating Systems Design and Implementation (OSDI '12) (pp. 251-264). [Enlace](https://static.googleusercontent.com/media/research.google.com/es//archive/spanner-osdi2012.pdf)
2. Google Cloud. (s.f.). *TrueTime and external consistency*. Google Cloud Spanner Documentation. [Enlace](https://cloud.google.com/spanner/docs/true-time-external-consistency)
3. Apache Software Foundation. (s.f.). *Apache Kafka Documentation: Message Delivery Semantics*. [Enlace](https://kafka.apache.org/documentation/#semantics)
4. Confluent. (s.f.). *Does Kafka guarantee message ordering?*. Confluent Blog. [Enlace](https://www.confluent.io/blog/does-kafka-guarantee-message-ordering/)
5. RabbitMQ. (s.f.). *Reliability Guide*. [Enlace](https://www.rabbitmq.com/docs/reliability#routing)
6. CloudAMQP. (s.f.). *RabbitMQ Message Order of Delivery*. CloudAMQP Blog. [Enlace](https://www.cloudamqp.com/blog/rabbitmq-message-order-of-delivery.html)