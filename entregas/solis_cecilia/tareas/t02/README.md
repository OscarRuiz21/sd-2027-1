## T02: Dos algoritmos de consenso que no son Raft
# Introducción
En un sistema distribuido participan varias computadoras, también llamadas nodos, que necesitan ponerse de acuerdo sobre la información que van a guardar. Esto puede complicarse cuando alguna falla o manda información incorrecta. Para resolverlo existen los protocolos de consenso. En esta tarea explicaré PBFT y Proof of Work.
# 1. PBFT: Practical Byzantine Fault Tolerance
PBFT permite que los nodos se pongan de acuerdo incluso cuando algunos se comportan de manera incorrecta. Esto se conoce como una falla bizantina y puede incluir enviar información falsa o mensajes diferentes a cada participante.
El proceso comienza cuando un cliente manda una solicitud. Un nodo que funciona como líder propone el orden en que se atenderá. Después, los demás intercambian mensajes para verificar que coinciden sobre la propuesta. Las fases principales son pre-prepare, prepare y commit. Al reunir las confirmaciones necesarias, ejecutan la operación. Si el líder falla, existe un procedimiento para cambiarlo.
Por ejemplo, con cuatro nodos se puede tolerar que uno falle o mienta. La regla es contar con al menos 3f + 1 nodos, donde f representa la cantidad de nodos defectuosos que se quiere tolerar. Por eso, la decisión no depende solamente de lo que diga el líder.
Su ventaja es que puede soportar comportamientos maliciosos. Una limitación es la cantidad de mensajes que intercambian los nodos. Además, para seguir avanzando necesita que la comunicación permita recibir los mensajes a tiempo eventualmente.
Fuente: Castro y Liskov, Practical Byzantine Fault Tolerance.
# 2. Proof of Work: Prueba de trabajo
Proof of Work es un mecanismo utilizado en Bitcoin como parte de su sistema de consenso. Consiste en demostrar que se realizó trabajo computacional antes de agregar un bloque de transacciones.
Los mineros reúnen transacciones y prueban distintos valores hasta encontrar un hash que cumpla el objetivo de dificultad. Un hash es una especie de huella digital calculada a partir de los datos. Encontrar uno que cumpla la condición requiere muchos intentos, pero verificarlo es sencillo.
Cuando un minero encuentra una solución, comparte el bloque. Los demás nodos revisan la prueba de trabajo y las transacciones. Si existen cadenas alternativas válidas, siguen la que tiene más trabajo acumulado.
Por ejemplo, si alguien quisiera modificar una transacción antigua, tendría que rehacer el trabajo del bloque y de los siguientes, además de alcanzar a la cadena honesta que continúa creciendo.
Su ventaja es que hace costoso modificar el historial. Sus limitaciones son el consumo de recursos y que una transacción no queda confirmada de forma absoluta: con más bloques posteriores disminuye la probabilidad de que se revierta, bajo el supuesto de que la mayoría del poder de cómputo sea honesto.
Fuente: Nakamoto, Bitcoin: A Peer-to-Peer Electronic Cash System.
# Conclusión
Los dos mecanismos ayudan a mantener un acuerdo, pero lo hacen de distinta manera. PBFT utiliza mensajes y confirmaciones entre los nodos. En Bitcoin, la prueba de trabajo y el trabajo acumulado permiten decidir qué cadena válida seguir. Esto muestra que un mismo problema puede resolverse con reglas diferentes dependiendo del sistema.