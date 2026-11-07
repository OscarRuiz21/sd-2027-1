# T04 · ¿Qué reloj usan las tecnologías que conoces?

## Tecnologías

### 1. Oracle Database

**1. ¿Qué es y para qué se usa?**

Es un sistema gestor de bases de datos relacionales que permite almacenar, consultar y modificar información. Se usa comúnmente en bases de datos relacionales de gran tamaño y de forma transaccional, pues soporta múltiples operaciones al mismo tiempo.

**2. ¿Qué tiempo utiliza para ordenar?**

Utiliza un sistema llamado *System Change Number* (SCN), el cual es un número interno que avanza conforme cambia el estado de la base de datos. Este sistema no depende de la hora del sistema operativo. Aunque no es un reloj de Lamport, comparte la idea de representar el orden lógico mediante números.

**3. ¿Qué orden garantiza?**

Permite ordenar las versiones confirmadas de la información dentro de una base de datos. Un valor de SCN 100 indica un estado anterior a uno con SCN 105. En una transacción distribuida, ayuda a coordinar los SCN de las bases participantes. No todas las bases de datos Oracle tienen el mismo reloj.

**4. ¿Qué ocurre cuando los relojes no coinciden?**

Oracle no necesita que los relojes de las computadoras tengan la misma hora para ordenar sus transacciones. En operaciones distribuidas, el gestor busca coordinar los SCN. Para confirmar una transacción distribuida, utiliza un SCN acordado entre los nodos involucrados. Así se mantiene la consistencia sin depender únicamente de NTP.

### 2. Redis

**1. ¿Qué es y para qué se usa?**

Es una base de datos que almacena información principalmente en memoria RAM, lo que permite realizar operaciones de manera rápida. Se utiliza para caché, almacenamiento de sesiones y contadores.

**2. ¿Qué tiempo utiliza para ordenar?**

No utiliza un reloj de Lamport ni vectorial para ordenar sus comandos. En una instancia normal, procesa los comandos secuencialmente.

Usa una estructura llamada **Redis Streams**, donde los mensajes reciben un identificador con el formato `milisegundos-númeroDeSecuencia`.

La primera parte se basa en el reloj físico del servidor y la segunda distingue mensajes generados en el mismo milisegundo. Por eso, combina tiempo físico y un número de secuencia.

**3. ¿Qué orden garantiza?**

Garantiza una ejecución secuencial de los comandos dentro de una misma instancia. Dentro de cada *stream* se puede conocer el orden de los mensajes. Sin embargo, no hay un orden global entre servidores ni entre *streams* distintos.

**4. ¿Qué ocurre cuando los relojes no coinciden?**

Si el tiempo actual es menor que el utilizado por el último mensaje, Redis conserva el valor temporal anterior y aumenta el número de secuencia. Así mantiene el orden creciente de los identificadores dentro del *stream*.

### 3. Riak KV

**1. ¿Qué es y para qué se usa?**

Es una base de datos NoSQL del tipo llave-valor que permite almacenar información entre varios servidores.

**2. ¿Qué tiempo utiliza para ordenar?**

Utilizaba *vector clocks* (relojes vectoriales) y, en versiones posteriores, incorporó *Dotted Version Vectors*. Estos registran información sobre los cambios realizados a cada objeto.

**3. ¿Qué orden garantiza?**

Permite reconocer relaciones causales y modificaciones concurrentes. No garantiza un orden total de todos los eventos del sistema.

**4. ¿Qué ocurre cuando los relojes no coinciden?**

Cuando detecta dos modificaciones concurrentes, puede conservar ambas como versiones hermanas (*siblings*). La aplicación puede decidir cómo combinar los valores o cuál conservar.

Se puede configurar la selección de un valor mediante marcas de tiempo, aunque esto puede provocar la pérdida de actualizaciones cuando hay conflictos.

## Referencias

Oracle. (s. f.). *Transactions*. Oracle Database Concepts. https://docs.oracle.com/en/database/oracle/oracle-database/19/cncpt/transactions.html

Oracle. (s. f.). *Distributed transactions concepts*. Oracle Database Administrator's Guide. https://docs.oracle.com/en/database/oracle/oracle-database/19/admin/distributed-transactions-concepts.html

Redis. (s. f.). *Redis streams*. Redis Docs. https://redis.io/docs/latest/develop/data-types/streams/

Redis. (s. f.). *XADD*. Redis Docs. https://redis.io/docs/latest/commands/xadd/

Redis. (s. f.). *Redis latency diagnosis*. Redis Docs. https://redis.io/docs/latest/management/optimization/latency/

Riak. (s. f.). *Causal context*. Riak KV 2.2.0 Documentation. https://docs.riak.com/riak/kv/2.2.0/learn/concepts/causal-context/index.html

Riak. (s. f.). *Conflict resolution*. Riak KV 2.2.0 Documentation. https://docs.riak.com/riak/kv/2.2.0/developing/usage/conflict-resolution.1.html
