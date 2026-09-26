# Tarea 02: Análisis Comparativo de Algoritmos de Consenso en PBFT

## 1. Algoritmo 1: PBFT basado en Códigos de Autenticación de Mensajes (MACs)

El primer algoritmo corresponde a la versión optimizada y práctica propuesta originalmente para el protocolo Practical Byzantine Fault Tolerance (PBFT). En esta variante, el objetivo principal es lograr consenso entre los nodos de un sistema distribuido minimizando la sobrecarga en el procesamiento de datos. 

Para lograrlo, el protocolo utiliza criptografía de clave simétrica mediante vectores de Códigos de Autenticación de Mensajes (MACs, por sus siglas en inglés) en lugar de firmas digitales para la comunicación diaria entre nodos. Cuando el nodo primario recibe una solicitud de un cliente, le asigna un número de secuencia y transmite el mensaje a las demás réplicas en la fase de *Pre-Prepare*. Posteriormente, los nodos avanzan por las fases de *Prepare* y *Commit* intercambiando validaciones protegidas por estos vectores MAC. 

La principal ventaja de este enfoque es su alta velocidad: al apoyarse en operaciones de dispersión (hash) y claves compartidas, la CPU de cada nodo requiere un esfuerzo computacional mínimo para verificar los mensajes. Esto permite que el sistema procese un volumen muy alto de peticiones con muy baja latencia. La única contrapartida es que el tamaño del vector MAC crece de forma lineal según la cantidad de nodos en la red, y las firmas asimétricas no se eliminan del todo, sino que se reservan únicamente para eventos críticos y poco frecuentes como el cambio de vista (*view-change*) o la creación de puntos de control (*checkpoints*).

## 2. Algoritmo 2: PBFT basado en Criptografía de Clave Pública

El segundo algoritmo representa la versión teórica o tradicional de PBFT, la cual basa la seguridad y autenticidad de toda la red exclusivamente en el uso de criptografía asimétrica o de clave pública.

En este esquema, cada acción y mensaje enviado durante el flujo de trabajo pasa por un proceso de firma digital. Desde la petición inicial del cliente hasta las fases de *Pre-Prepare*, *Prepare*, *Commit* y la respuesta final (*Reply*), cada nodo utiliza su clave privada para firmar individualmente cada paquete de datos que transmite a la red. Las demás réplicas, al recibir la información, emplean la clave pública del emisor para validar su autenticidad.

La ventaja conceptual de este algoritmo es la simplicidad en la verificación y el no repudio: cualquier nodo o tercero puede comprobar de manera directa que un mensaje fue generado por una réplica específica, manteniendo el tamaño del encabezado del mensaje constante sin importar cuántos nodos integren el sistema. Sin embargo, en la práctica resulta sumamente ineficiente. Como las fases de *Prepare* y *Commit* exigen un intercambio de mensajes de todos contra todos, la necesidad de generar y verificar miles de firmas asimétricas por segundo genera un cuello de botella en el procesador, provocando que el rendimiento de la red caiga drásticamente y aumentando los tiempos de respuesta.

## 3. Referencias

* Castro, M., & Liskov, B. (1999). Practical Byzantine fault tolerance. En *Proceedings of the Third Symposium on Operating Systems Design and Implementation* (OSDI '99, pp. 173–186). https://css.csail.mit.edu/6.824/2014/papers/castro-practicalbft.pdf
* Bit2Me Academy. (2026, 15 de mayo). *¿Qué es PoA (Proof of Authority – Prueba de Autoridad)?* Bit2Me Academy. https://academy.bit2me.com/que-es-proof-of-authority-poa/
* Ethereum Community. (s. f.). *Proof-of-authority (PoA)*. Ethereum.org. Recuperado de https://ethereum.org/developers/docs/consensus-mechanisms/poa/
