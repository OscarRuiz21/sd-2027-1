# T04 · ¿Qué reloj usan las tecnologías que conoces?
 
Sistemas Distribuidos

Javier Velasco Pacheco


En esta tarea se revisan PostgreSQL, RabbitMQ y Redis Streams. Son tecnologías relacionadas con bases de datos y mensajería, y cada una resuelve el problema del orden de una forma distinta.

## PostgreSQL

PostgreSQL es un sistema gestor de bases de datos que se utiliza para guardar, organizar, consultar y modificar información. Por ejemplo, permite almacenar usuarios, productos, pagos y transferencias. También puede generar números automáticos para identificar registros, como los ID de usuarios o transferencias.

Utiliza **una secuencia de números** para generar los ID. Estas asignan un número diferente a cada registro, incluso cuando varias sesiones solicitan un ID al mismo tiempo. Este mecanismo no utiliza un reloj físico ni un reloj de Lamport.

Sin embargo, el orden de los ID no siempre coincide con el orden en que terminan las transacciones. Por ejemplo, una operación puede recibir el ID 10 y terminar después de otra que recibió el 11. Además, pueden quedar números sin utilizar cuando una operación falla.

Finalmente, la sincronización de relojes mediante Network Time Protocol no afecta estas secuencias, ya que funcionan independientemente de la hora de los equipos. Para ordenar los registros se puede utilizar ORDER BY.

## RabbitMQ

RabbitMQ es un intermediario de mensajes que permite que diferentes aplicaciones y servicios se comuniquen mediante el envío y recepción de mensajes. Se utiliza para realizar tareas de forma asíncrona, como enviar notificaciones o procesar transferencias.

**Utiliza el orden FIFO (First In, First Out)**, es decir, el primer mensaje que entra a una cola es el primero en salir. El orden depende de cuándo RabbitMQ recibe los mensajes, no de la hora de los equipos.

Este orden se garantiza dentro de una misma cola, pero puede cambiar si existen varios consumidores, prioridades o reenvíos de mensajes. Además, no garantiza un orden entre diferentes colas.

Por último, si los relojes de los equipos no coinciden, no afecta el orden de los mensajes, ya que RabbitMQ utiliza el orden de llegada y no las horas de los dispositivos.

## Redis Streams

Redis es un sistema que almacena datos en memoria para acceder a ellos rápidamente. Redis Streams permite guardar y consultar eventos, como notificaciones, transferencias o lecturas de sensores.

Cada evento recibe un ID formado por dos partes: la hora en milisegundos y un número de secuencia.

Redis garantiza que los ID aumenten dentro de un mismo stream, pero no garantiza un orden global entre diferentes streams.

Si el reloj del servidor se atrasa, Redis utiliza el último ID generado y aumenta el número de secuencia para mantener el orden. De esta manera, los eventos conservan su orden aunque el reloj del servidor no esté sincronizado correctamente.
## Comparación

| Tecnología | Mecanismo de orden | Alcance principal | Límite importante |
|---|---|---|---|
| PostgreSQL | Número de secuencia asignado por el servidor | Valores únicos de una secuencia compartida | El ID no equivale al orden de confirmación; puede haber huecos. |
| RabbitMQ | Orden de encolado FIFO | Dentro de una cola y bajo condiciones normales | Canales concurrentes, prioridades, consumidores y reentregas pueden afectar el orden observado. |
| Redis Streams | ID de milisegundos + secuencia | Orden total dentro de un stream | No establece por sí solo un orden causal/global entre streams diferentes. |

## Fuentes consultadas

- PostgreSQL Global Development Group. (s. f.). *Sequence Manipulation Functions*. PostgreSQL Documentation. https://www.postgresql.org/docs/current/functions-sequence.html
- RabbitMQ. (s. f.). *Queues: Message ordering*. RabbitMQ Documentation. https://www.rabbitmq.com/docs/queues
- Redis. (s. f.). *Redis Streams*. Redis Documentation. https://redis.io/docs/latest/develop/data-types/streams/
- Redis. (s. f.). *XADD command*. Redis Documentation. https://redis.io/docs/latest/commands/xadd/

---

# Tarea opcional · Reloj de Lamport en Java

## Descripción

Este programa simula tres servidores (A, B y C) que realizan eventos locales y se envían cinco mensajes entre ellos. Cada servidor tiene su propio reloj lógico, implementado con AtomicLong, para llevar el registro del orden de sus eventos.

Cada vez que un servidor realiza una acción o envía un mensaje, su reloj aumenta en 1. Cuando recibe un mensaje, compara su reloj con el valor recibido, toma el número mayor y le suma 1. Por ejemplo, si el servidor A tiene su reloj en 3 y recibe un mensaje de B con el valor 5, actualiza su reloj a 6. Esto permite que cada servidor tome en cuenta los eventos anteriores de los demás.

El reloj lógico no representa la hora real, sino que permite mantener el orden de los eventos y respetar la relación causal entre los servidores.

## Código:

```java
import java.util.concurrent.atomic.AtomicLong;

public class Lamport {
    static class Servidor {
        private final String nombre;
        private final AtomicLong reloj = new AtomicLong(0);

        Servidor(String nombre) {
            this.nombre = nombre;
        }

        long eventoLocal(String descripcion) {
            long t = reloj.incrementAndGet();
            imprimir(descripcion, t);
            return t;
        }

        void enviar(Servidor destino, String mensaje) {
            long tEnvio = reloj.incrementAndGet();
            imprimir("ENVÍA a " + destino.nombre + ": " + mensaje, tEnvio);
            destino.recibir(this.nombre, mensaje, tEnvio);
        }

        void recibir(String origen, String mensaje, long relojRecibido) {
            long t = reloj.updateAndGet(actual -> Math.max(actual, relojRecibido) + 1);
            imprimir("RECIBE de " + origen + ": " + mensaje, t);
        }

        private void imprimir(String evento, long t) {
            System.out.printf("%-8s | reloj=%-2d | %s%n", nombre, t, evento);
        }
    }

    public static void main(String[] args) {
        Servidor A = new Servidor("Servidor A");
        Servidor B = new Servidor("Servidor B");
        Servidor C = new Servidor("Servidor C");

        A.eventoLocal("inicia una solicitud");
        A.enviar(B, "M1: solicitud");
        B.enviar(C, "M2: solicitud recibida y reenviada");
        C.eventoLocal("registra la solicitud");
        C.enviar(A, "M3: confirmación");
        A.enviar(C, "M4: consulta de estado");
        B.enviar(A, "M5: aviso independiente");
    }
}
```

## Compilación

Se necesita tener instalado un JDK y que los comandos `javac` y `java` estén disponibles en la terminal.

Desde la carpeta `lamport/`, ejecutar:

```bash
javac Lamport.java
java Lamport
```

## Salida 

```text
Servidor A | reloj=1  | inicia una solicitud
Servidor A | reloj=2  | ENVÍA a Servidor B: M1: solicitud
Servidor B | reloj=3  | RECIBE de Servidor A: M1: solicitud
Servidor B | reloj=4  | ENVÍA a Servidor C: M2: solicitud recibida y reenviada
Servidor C | reloj=5  | RECIBE de Servidor B: M2: solicitud recibida y reenviada
Servidor C | reloj=6  | registra la solicitud
Servidor C | reloj=7  | ENVÍA a Servidor A: M3: confirmación
Servidor A | reloj=8  | RECIBE de Servidor C: M3: confirmación
Servidor A | reloj=9  | ENVÍA a Servidor C: M4: consulta de estado
Servidor C | reloj=10 | RECIBE de Servidor A: M4: consulta de estado
Servidor B | reloj=5  | ENVÍA a Servidor A: M5: aviso independiente
Servidor A | reloj=10 | RECIBE de Servidor B: M5: aviso independiente
```

El programa respeta la causalidad porque cada mensaje conserva el valor del reloj del servidor que lo envía y el receptor actualiza su reloj a un valor mayor. Por ejemplo, A envía M1 con reloj 2 y B lo recibe con reloj 3. Después, B envía M2 con reloj 4 y C lo recibe con reloj 5.

De esta manera, los eventos relacionados causalmente mantienen un orden lógico. Sin embargo, esto no significa que los eventos independientes o concurrentes tengan un orden real entre sí.