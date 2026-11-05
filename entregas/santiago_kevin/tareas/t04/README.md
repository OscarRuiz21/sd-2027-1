# T04 · ¿Qué reloj usan las tecnologías que conoces?

**Alumno:** Kevin Santiago González

## 1. MongoDB

### ¿Qué es y para qué se usa?

MongoDB es una base de datos que almacena información en documentos con una
estructura similar a JSON. Se utiliza para guardar y consultar datos de
aplicaciones, como usuarios, productos y pedidos. También permite distribuir
los datos entre varios servidores mediante sharding.

### ¿Qué tiempo usa para ordenar?

Utiliza un reloj lógico híbrido basado en Lamport. Los participantes intercambian el último tiempo lógico que conocen,
y los servidores primarios lo utilizan para asignar tiempos a nuevas
escrituras.

### ¿Qué orden garantiza y hasta dónde?

En una sesión con consistencia causal, las operaciones secuenciales respetan
sus relaciones de causa y efecto.

Para obtener todas las garantías documentadas se utilizan
readConcern: "majority" y writeConcern: "majority".
El alcance corresponde a las operaciones relacionadas de la sesión.
no significa que todas las operaciones independientes del sistema tengan
un único orden global.

### ¿Qué pasa cuando los relojes no coinciden?

Los participantes actualizan su tiempo lógico mediante los mensajes que
intercambian. Si una lectura necesita observar una escritura que todavía
no llega a una réplica, esta puede esperar hasta alcanzarla.


## 2. PostgreSQL

### ¿Qué es y para qué se usa?

PostgreSQL es un sistema de bases de datos relacional de código abierto
que utiliza SQL. Sirve para almacenar información estructurada y realizar
consultas y transacciones, por ejemplo, sobre cuentas y movimientos bancarios.


### ¿Qué tiempo usa para ordenar?

Los registros del WAL se ordenan mediante un LSN, que significa
Log Sequence Number. Es una posición en bytes dentro del registro que
aumenta conforme se agregan nuevos registros.

Este mecanismo utiliza un número de secuencia basado en posiciones, 
en lugar de comparar las horas de los servidores.

### ¿Qué orden garantiza y hasta dónde?

Permite establecer el orden de los registros dentro de un mismo flujo WAL
y una misma línea de tiempo. Un registro con una posición menor aparece
antes que otro con una posición mayor.

Este orden permite seguir el avance de la recuperación y la replicación.
Los LSN no constituyen un reloj global compartido por servidores
PostgreSQL independientes.

### ¿Qué pasa cuando los relojes no coinciden?

De la definición del LSN se deduce que una diferencia entre las horas
de los servidores no cambia el orden de los registros del WAL, porque
este depende de sus posiciones.

## 3. Redis

### ¿Qué es y para qué se usa?

Redis es un almacén de datos en memoria que se utiliza como caché y base de 
datos. Hablaré de Redis Streams, una estructura
que permite registrar y consumir eventos, como lecturas de sensores o
notificaciones.

### ¿Qué tiempo usa para ordenar?

Cuando se agrega un evento con XADD y un identificador automático,
Redis combina la hora física del servidor en milisegundos con un número
de secuencia. El formato es:

milisegundos-secuencia

El número de secuencia permite distinguir eventos agregados durante
el mismo milisegundo. Por eso, el mecanismo combina tiempo físico con
un contador.

### ¿Qué orden garantiza y hasta dónde?

Garantiza un orden total de las entradas dentro de cada stream:
cada identificador nuevo debe ser mayor que el anterior.

El alcance es un stream, asociado a una llave. Esta garantía no establece
un orden global entre streams diferentes.

### ¿Qué pasa cuando los relojes no coinciden?

Si el reloj retrocede, o un nuevo servidor principal tiene una hora menor
que la del último identificador, Redis conserva el componente temporal
de ese identificador e incrementa la secuencia.

## Opcional: simulación de un reloj de Lamport en Java

### Funcionamiento

Simulé tres servidores: S1, S2 y S3. Cada servidor tiene su propio
reloj lógico, almacenado en un AtomicLong que comienza en cero.
Se intercambian cinco mensajes.

La simulación se ejecuta de forma secuencial en un programa:
cada objeto representa un servidor y las llamadas a sus métodos
representan eventos. M1 se conserva temporalmente antes de entregarlo
para simular un retraso de red.

El reloj se actualiza con estas reglas:

- Evento local: se incrementa en uno.
- Envío: se incrementa en uno y ese valor se adjunta al mensaje.
- Recepción: se actualiza a max(reloj local, marca recibida) + 1.

### ¿Por qué la salida respeta la causalidad?

Dentro de cada servidor, el reloj aumenta en cada evento.
Además, cada recepción tiene un reloj mayor que el envío correspondiente:

| Mensaje | Ruta | Reloj al enviar | Reloj al recibir |
| --- | --- | ---: | ---: |
| M1 | S1 → S2 | 2 | 7 |
| M2 | S1 → S3 | 3 | 4 |
| M3 | S3 → S2 | 5 | 6 |
| M4 | S2 → S1 | 8 | 9 |
| M5 | S1 → S3 | 11 | 12 |

Por ejemplo, S2 recibe primero M3 y su reloj llega a 6.
Después recibe M1, que trae la marca 2. Aplica max(6, 2) + 1 = 7,
por lo que su reloj sigue avanzando aunque el mensaje esté retrasado.

El orden local y la relación entre envío y recepción se conservan.

La relación inversa no está garantizada: que un evento tenga un reloj
menor que otro no basta para afirmar que lo causó.

## Fuentes oficiales

### MongoDB

- [Descripción general de MongoDB](https://www.mongodb.com/docs/manual/)
- [Causal Guarantees Are Anything but Casual](https://www.mongodb.com/company/blog/engineering/casual-guarantees-anything-casual)
- [Read Isolation, Consistency, and Recency](https://www.mongodb.com/docs/manual/core/read-isolation-consistency-recency/)

### PostgreSQL

- [Descripción general de PostgreSQL](https://www.postgresql.org/about/)
- [WAL Internals](https://www.postgresql.org/docs/current/wal-internals.html)
- [Tipo pg_lsn](https://www.postgresql.org/docs/current/datatype-pg-lsn.html)

### Redis

- [Introducción a Redis](https://redis.io/docs/latest/)
- [Redis Streams](https://redis.io/docs/latest/develop/data-types/streams/)
- [Comando XADD](https://redis.io/docs/latest/commands/xadd/)
