## PBFT (Practical Byzantine Fault Tolerance) ## 
fue diseñado para resolver problemas de fallas bizantinas (aquellas en las que un sistema distribuido recibe información contradictoria de múltiples fuentes y ante esto no se puede verificar que componente exacto falla) y es muy utilizado en la tecnología de blockchain y permisos de redes.

Este algoritmo busca generar un consenso incluso cuando diferentes nodos presentan fallos, o información falsa. Esto tomando sólo en cuenta a las replicas "honestas".

Se divide en las siguientes fases:

1. Pre-preparación: Un nodo primario (líder designado), recibe una petición del cliente y las empaqueta en un bloque con una secuencia numérica única para después transmitir este mensaje a todos los nodos de respaldo (replicas) con una prueba de la secuencia numérica, iniciando así el proceso de consenso e informando a los otros nodos acerca del orden propuesto de operaciones.

2. Preparación: Cada replica que recibió el mensaje de la pre-preparación lo válida y transmite un mensaje de preparación a todas las demás replicas. Estos mensajes, deben de ser confirmados por al menos 2/3 de los demás nodos en la red. Al confirmarse que la mayoría obtuvo el mismo mensaje de pre-preparación y este fue validado, se entra en un nuevo estado llamado preparado.

3. Confirmación (commit): Una vez que un nodo se encuentra en el estado preparado, transmite un mensaje de confirmación a las demás replicas y de igual manera, cuando se obtiene una confirmación sobre el contenido del mensaje de la mayoría de los nodos alcanzan un estado de preparación, ahora pasan a un estado de confirmación y finalmente ejecuta el bloque. Con esto se asegura que todas las replicas honestas ven la misma secuencia de bloques y se ejecutan en el mismo orden, asegurando con esto consistencia.

En cambio, si la replica primaria provee información defectuosa o lenta, PBFT incluye un mecanismo que permite seleccionar otra replica primaria. Además, las replicas cercanas vigilan a la replica primaria y su desempeño. Si estas replicas no reciben mensajes recurrentes, activan un cambio de vista y buscan coordinarse en la elección de una nueva replica primaria para continuar el consenso.

Los cambios de vista añaden complejidad pero son esenciales para asegurar la persistencia del sistema incluso cuando el líder falla. Los cambios de vista en esencia es un cambio de nodo primario, se realiza cuando este falla en comunicarse con los demás. En este caso el nodo secundario detiene el procesamiento de nuevas transacciones en la vista actual y transmite un mensaje de tipo VIEW-CHANGE a todos los demás nodos de la red. Este mensaje incluye un nuevo número de vista, el último checkpoint estable y un conjunto de mensajes de preparación.


## POW (Proof of Work) ##
Es usado en bitcoin y otras criptomonedas, es de los algoritmos de consenso más conocidos, este utiliza nodos llamados mineros, que esencialmente son computadoras que participan en la red para resolver puzzles (o acertijos) complejos de criptografía para validar transacciones y añadir nuevos bloques a la blockchain. Consume mucha energía pero provee seguridad robusta.

Se busca obtener un comportamiento honesto de los nodos de una red, ya que se les exige la realización de un trabajo considerable pero realizable y a cambio, a los participantes se les recompensa de forma monetaria

## Bibliografía ##

**_PBFT_**

* Tolerancia a las fallas bizantinas, una guía rápida – Ciberseguridad. (n.d.). https://ciberseguridad.com/guias/nuevas-tecnologias/criptomoneda/tolerancia-fallas-bizantinas/

* CodeLucky. (2025, December 12). PBFT explained: Byzantine fault tolerance for beginners [Video]. YouTube. https://www.youtube.com/watch?v=KPmZtBjUL_k

* Xie, Y. (2022) ALGORITMOS DE CONSENSO Y LA BLOCKCHAIN. UNAM - Dirección General de Bibliotecas, https://ru.dgb.unam.mx/server/api/core/bitstreams/27a18e72-1617-4456-97a6-b92184d28fd3/content

**_POW_**

* GeeksforGeeks. (2025, August 8). Consensus algorithms in distributed system. GeeksforGeeks. https://www.geeksforgeeks.org/operating-systems/consensus-algorithms-in-distributed-system/

* Leal, A. (2025, March 10). Algoritmos de Consenso: Prueba de Trabajo (PoW) vs. Prueba de Participación (PoS). CriptoNoticias - Noticias De Bitcoin, Ethereum Y Criptomonedas. https://www.criptonoticias.com/criptopedia/algoritmos-consenso-prueba-trabajo-pow-prueba-participacion-pos/