Primeramente levantamos el proyecto desde la carpeta t01, donde Docker construyo las imágenes del servidor y del cliente, creo la red t01\_red\_t01 y arranco los dos contenedores. El servidor quedo corriendo con los puertos 5000 (REST) y 50051 (gRPC) publicados, y el cliente termino con código 0, lo que significa que hizo sus consultas sin errores.



La lógica del servidor se encontrara implementada dentro de la función buscar en lógica.py, donde se recibe un ID y devuelve los datos de la persona o nada si el ID de la persona no existe. Esa función no sabe si la llaman por REST o por gRPC. El servidor, servidor.py, es un solo programa que tiene dos controladores, uno por cada forma de comunicarse. El controlador REST recibe el ID en la URL y responde en JSON. El controlador gRPC recibe el ID en un mensaje definido en el .proto y responde con otro mensaje. Los dos hacen lo mismo llamando a la función de búsqueda y estructurando la respuesta, respecto su propio formato de entrega.



De igual manera revisamos lo que hizo el cliente. Desde su contenedor encuentra al servidor por su nombre dentro de la red de Docker y consulta el mismo dato por las dos interfaces. Con el ID 1 que existe, obtuvimos los mismos datos por REST y por gRPC:



&#x20;   --- ID 1 ---

&#x20;   REST: {'edad': 27, 'encontrado': True, 'nombre': 'Oscar Manuel', 'rol': 'CEO'}

&#x20;   gRPC: {'encontrado': True, 'nombre': 'Oscar Manuel', 'rol': 'CEO', 'edad': 27}



Con el ID 777 que no existe, las dos interfaces respondieron que no hay datos. El formato cambia, porque REST manda un JSON con un mensaje y gRPC manda encontrado en false, pero la decisión la toma la misma logica:



&#x20;   --- ID 777 ---

&#x20;   REST: {'encontrado': False, 'mensaje': 'no hay datos'}

&#x20;   gRPC: {'encontrado': False}



En los logs del servidor se ve que un solo programa anuncia las dos interfaces, y que las peticiones REST llegan desde la dirección interna del contenedor del cliente, así mismo, probamos el REST desde la computadora con curl y obtuvimos las mismas respuestas.



Finalmente cabe mencionar que las salidas de los archivos como evidenciaestado.txt, evidencialogs.txt y evidenciacurl.txt están guardadas junto a nuestro documento evidencias y respaldan todo lo descrito. En evidenciaestado.txt se ve que el servidor quedo en ejecución y que el cliente termino sin errores, en evidencialogs.txt están los logs completos del servidor y del cliente, donde se aprecia que un solo programa anuncia las dos interfaces y las respuestas que recibió el cliente por cada una y concluyendo así en evidenciacurl.txt están las respuestas completas de curl con sus cabeceras, con las que comprobamos que el REST también responde desde la computadora. 

