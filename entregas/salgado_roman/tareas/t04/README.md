# Tarea 04: Tiempo y orden en sistemas distribuidos
 
**Alumno:** Emiliano Román Salgado Cruz
**Materia:** Sistemas Distribuidos · FI-UNAM · 2027-1
 
Para cada sistema se responde: qué es y para qué se usa, qué tiempo usa para ordenar, qué orden garantiza y hasta dónde, y qué pasa cuando los relojes no coinciden.
 
## Tabla resumen
 
| Sistema | Tiempo que usa | Orden que garantiza | Alcance |
|---|---|---|---|
| Google Spanner | Reloj físico (TrueTime, con intervalo de incertidumbre) | Estricto (consistencia externa / *linearizability*) | Global |
| Apache Kafka | Números de secuencia (*offset*) | Estricto | Solo dentro de una partición |
| MongoDB | Reloj lógico híbrido (HLC) | Causal | Todo el conjunto de réplicas |
 
---
 
## 1. Google Spanner (TrueTime)
 
- **Qué es y para qué se usa:** Es una base de datos relacional distribuida a nivel global creada por Google. Se usa para aplicaciones que necesitan las garantías de una base de datos tradicional (transacciones ACID) pero que operan a una escala masiva y distribuida en múltiples centros de datos alrededor del mundo.
- **Qué tiempo usa para ordenar:** Usa un reloj físico avanzado, pero a través de una API especial llamada TrueTime. TrueTime utiliza hardware especializado (relojes atómicos y receptores GPS) distribuidos en sus centros de datos. En lugar de devolver un instante de tiempo exacto, devuelve un "intervalo de tiempo" (un momento más temprano y uno más tardío) que representa la incertidumbre del reloj.
- **Qué orden te garantiza y hasta dónde:** Garantiza un orden estricto a nivel global (consistencia externa o *linearizability*). Esto significa que si una transacción se confirma (*commit*) antes de que otra comience, el sistema garantiza que la primera aparecerá en el historial antes que la segunda, en todo el sistema.
- **Qué pasa cuando los relojes no coinciden:** Como los relojes físicos nunca son perfectos, Spanner se basa en la "incertidumbre" de TrueTime. Para evitar problemas de orden, utiliza una técnica llamada *Commit Wait*: antes de confirmar una transacción, el servidor literalmente "espera" a que pase todo el margen de error (el intervalo de incertidumbre) garantizando así que ningún otro nodo pueda generar un evento que se traslape temporalmente.
## 2. Apache Kafka
 
- **Qué es y para qué se usa:** Es una plataforma distribuida de transmisión de eventos (*event streaming*). Se usa principalmente para crear *pipelines* de datos en tiempo real, procesar flujos de información y comunicar microservicios desacoplados.
- **Qué tiempo usa para ordenar:** Usa números de secuencia. Aunque a los mensajes se les puede adjuntar un *timestamp* físico al momento de crearse, Kafka delega el orden real de los eventos a un identificador numérico secuencial que se incrementa en uno llamado *offset* (desplazamiento), el cual es asignado por el servidor que recibe el mensaje.
- **Qué orden te garantiza y hasta dónde:** Garantiza un orden estricto, pero únicamente dentro de una misma partición. Si envías mensajes a la "Partición 0" de un tópico, se leerán en el orden exacto en que llegaron. Sin embargo, no hay ninguna garantía de orden global entre diferentes particiones.
- **Qué pasa cuando los relojes no coinciden:** A Kafka prácticamente "lo ignora" para fines de ordenamiento de lectura, porque su verdad absoluta es el número de secuencia (*offset*). Si el reloj físico de un productor está desincronizado, el *timestamp* del mensaje estará mal, pero el orden en el que se guarda y se consume dependerá de en qué momento exacto entró a la partición y qué número de secuencia se le asignó.
## 3. MongoDB
 
- **Qué es y para qué se usa:** Es una de las bases de datos NoSQL orientada a documentos más populares. Se utiliza para guardar información no estructurada o semiestructurada en formato BSON (similar a JSON), ideal para aplicaciones web modernas que necesitan esquemas flexibles.
- **Qué tiempo usa para ordenar:** A partir de sus versiones recientes (3.6+), usa Relojes Lógicos Híbridos (HLC) para mantener el orden de las operaciones. Este reloj combina una parte de reloj físico de pared (el *timestamp* de la máquina) con una parte de reloj lógico (un contador entero que se incrementa como los relojes de Lamport).
- **Qué orden te garantiza y hasta dónde:** Garantiza un orden causal en todo el conjunto de réplicas (*replica set*) de la partición de datos. Esto asegura que si una operación "A" causó lógicamente una operación "B" (por ejemplo, leí A y luego escribí B con base en eso), cualquier nodo en el sistema verá "A" antes que "B".
- **Qué pasa cuando los relojes no coinciden:** Si los relojes físicos (por NTP) se desvían un poco, el componente lógico del HLC entra en acción. Cuando un nodo recibe un mensaje con un reloj lógico híbrido mayor al suyo, adelanta su propio reloj para que coincida. Esto compensa los desfases del reloj de pared y mantiene la causalidad intacta sin necesidad de que el NTP esté perfectamente sincronizado en todo momento.
---
 
## Referencias
 
Corbett, J. C., Dean, J., Epstein, M., Fikes, A., Frost, C., Furman, J. J., ... & Woodford, D. (2012). Spanner: Google's globally-distributed database. *Proceedings of the 10th USENIX Symposium on Operating Systems Design and Implementation (OSDI 12)*, 251-264. https://www.usenix.org/system/files/conference/osdi12/osdi12-final-16.pdf
 
MongoDB, Inc. (2024). *Causal Consistency and Read/Write Concern*. MongoDB Manual. Recuperado el 7 de noviembre de 2026, de https://www.mongodb.com/docs/manual/core/causal-consistency-read-write-concerns/    

Apache Software Foundation. (s.f.). Apache Kafka documentation. Recuperado el 7 de noviembre de 2026, de https://kafka.apache.org/documentation/

Kreps, J., Narkhede, N., & Rao, J. (2011). Kafka: A distributed messaging system for log processing. Proceedings of the 6th International Workshop on Networking Meets Databases (NetDB’11), Athens, Greece.