# T04 · ¿Qué reloj usan las tecnologías que conoces?

**Alumna: Cecilia Ximena Solís Cisneros**

En un inicio se consideraron tecnologías que se han utilizado en otras materias, como Google Compute Engine y Oracle Database, además de MariaDB, cuya implementación se tiene planeada para un proyecto. Por lo que el objetivo de esta actividad fue investigar qué mecanismos utilizan para representar el tiempo y ordenar los cambios. Si bien, se tenía conocimiento de algunas de sus funciones, fue necesario revisar su documentación para comprender hasta dónde se puede confiar en dicho orden.

## 1. Google Compute Engine: reloj de las máquinas virtuales

### Qué es y para qué se usa

Google Compute Engine es un servicio de Google Cloud que permite crear y administrar máquinas virtuales. Se utilizó en prácticas para crear dichas máquinas y trabajar mediante su terminal. Para esta actividad se investigó cómo se sincroniza el reloj del sistema de las máquinas virtuales, pues sus registros pueden contener horas distintas aun cuando se encuentren dentro del mismo servicio.

### Qué tiempo usa para ordenar

Las máquinas cuentan con un reloj físico o de pared, que representa la fecha y la hora. De acuerdo con la documentación, las instancias vienen preconfiguradas para sincronizarlo mediante NTP. Esta hora permite poner marcas de tiempo en los registros; sin embargo, no establece por sí sola un orden causal entre máquinas. [1]

### Qué orden garantiza y hasta dónde

La sincronización del reloj no garantiza un orden global de los eventos entre las máquinas virtuales. Como ejemplo hipotético, una máquina podría registrar el envío de una solicitud a las 10:05 y otra registrar su recepción a las 10:03, por tener el reloj atrasado. La solicitud tuvo que enviarse antes de recibirse, aunque las horas aparenten lo contrario. Por lo que comparar las marcas de tiempo de sus registros no es suficiente para determinar la causalidad entre ambas máquinas.

### Qué pasa cuando los relojes no coinciden

NTP ayuda a corregir las diferencias entre los relojes de las máquinas virtuales. Si bien, esto permite acercar sus horas, no significa que sean idénticas en todo momento. Este mecanismo ajusta el reloj físico; no asigna una secuencia global a los eventos ni resuelve por sí solo cuál causó a otro. Se comprendió así que sincronizar la hora y garantizar el orden de los eventos son problemas distintos. [1]

## 2. Oracle Database: número de cambio del sistema

### Qué es y para qué se usa

Oracle Database es un sistema para almacenar y consultar información, el cual se ha utilizado en actividades de bases de datos. En esta ocasión se investigó cómo representa el avance de sus cambios.

### Qué tiempo usa para ordenar

Oracle utiliza el SCN, o número de cambio del sistema, como una marca de tiempo lógica interna. Su valor no retrocede y representa un punto en el avance de la base de datos. Por lo que no se interpreta como una hora del día. [2]

### Qué orden garantiza y hasta dónde

El SCN permite ordenar eventos dentro de la base de datos y se utiliza para mantener lecturas consistentes y apoyar la recuperación. Varios eventos pueden compartir un SCN, por lo que no corresponde a un turno exclusivo para cada operación. Tampoco permite comparar directamente los cambios de dos bases independientes. [2]

### Qué pasa cuando los relojes no coinciden

Para este mecanismo, el orden se representa mediante el SCN, en lugar de comparar las horas de las computadoras de los usuarios. Por lo que una diferencia entre dichas horas no determina qué cambio va primero. Esto no significa que Oracle deje de utilizar el reloj físico en otras funciones. [2]

## 3. MariaDB: registro binario para replicación

### Qué es y para qué se usa

MariaDB es un sistema de bases de datos relacionales, cuya implementación se tiene planeada para un proyecto. Si bien, todavía no se ha implementado, se eligió para investigar cómo un servidor puede compartir sus cambios con una réplica, es decir, con otro servidor que mantiene una copia de los datos.

### Qué tiempo usa para ordenar

Para este caso se analizó el registro binario, también llamado binlog. El servidor primario registra los eventos y estos se identifican mediante el nombre del archivo y una posición dentro de él. La posición es un desplazamiento en el archivo, no una hora ni un contador que aumente uno por cada transacción. Así, la réplica puede identificar desde qué punto debe continuar leyendo. [3], [4]

### Qué orden garantiza y hasta dónde

El orden corresponde al flujo de eventos del servidor primario. No representa un orden global entre servidores independientes. Además, tener una posición en el registro no significa que la réplica ya haya aplicado todos los cambios. [3]

Fue necesario distinguir el orden del registro del modo de ejecución de la réplica. En la replicación paralela configurada para conservar el orden, las transacciones pueden ejecutarse en paralelo, pero se confirman en el mismo orden que en el primario. Existen otros modos que permiten cambiar ese orden; por lo que no se debe afirmar que cualquier configuración tiene la misma garantía. [5]

### Qué pasa cuando los relojes no coinciden

Para solicitar los eventos pendientes se utiliza el archivo y la posición del registro, sin necesitar que las horas de ambas máquinas sean iguales. Por ejemplo, una réplica puede continuar desde una posición determinada aunque su reloj esté atrasado. Se deduce así que, en este mecanismo, la diferencia de horas no cambia el orden ya registrado en el binlog. [4]

## Reflexión

El análisis de estas tecnologías ayudó a comprender que sincronizar los relojes no es lo mismo que ordenar los cambios. En Compute Engine se estudió el reloj físico de las máquinas, mientras que en Oracle se identificó una referencia lógica y en MariaDB una posición dentro de un registro. Si bien, al inicio estos mecanismos podían parecer similares, fue necesario distinguir su función y su alcance, pues ninguno debe interpretarse como una garantía de orden para cualquier evento de todo un sistema distribuido.


## Referencias

[1] Google Cloud, “Configure time synchronization,” *Compute Engine Documentation*. [En línea]. Disponible en: https://docs.cloud.google.com/compute/docs/instances/time-synchronization. [Consultado: 6 de noviembre de 2026].

[2] Oracle, “System Change Numbers (SCNs),” en *Oracle Database Concepts*, versión 12.2. [En línea]. Disponible en: https://docs.oracle.com/en/database/oracle/oracle-database/12.2/cncpt/database-concepts.pdf. [Consultado: 6 de noviembre de 2026].

[3] MariaDB, “MariaDB Replication Overview for SQL Server Users,” *MariaDB Server Documentation*, sec. “Binary Log Coordinates, Relay Log Coordinates and GTID.” [En línea]. Disponible en: https://mariadb.com/docs/server/server-management/install-and-upgrade-mariadb/migrating-to-mariadb/migrating-to-mariadb-from-sql-server/mariadb-replication-overview-for-sql-server-users. [Consultado: 6 de noviembre de 2026].

[4] MariaDB, “COM_BINLOG_DUMP,” *MariaDB Server Documentation*. [En línea]. Disponible en: https://mariadb.com/docs/server/reference/clientserver-protocol/replication-protocol/com_binlog_dump. [Consultado: 6 de noviembre de 2026].

[5] MariaDB, “Parallel Replication,” *MariaDB Server Documentation*. [En línea]. Disponible en: https://mariadb.com/docs/server/ha-and-performance/standard-replication/parallel-replication. [Consultado: 6 de noviembre de 2026].
