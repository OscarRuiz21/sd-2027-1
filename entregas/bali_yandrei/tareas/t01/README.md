\# T01 - El mismo servicio por REST y gRPC



En esta tarea hice un servicio sencillo que recibe un ID y devuelve la informacion de un usuario.



La idea principal fue hacer una sola logica y despues poder consultarla de dos formas diferentes: usando REST y usando gRPC.



1. Logica compartida



La logica principal se encuentra en el archivo `servicio.py`.



En ese archivo tengo un diccionario con algunos usuarios y una funcion llamada `buscar\_usuario`.



Esta funcion recibe un ID y busca si existe un usuario con ese numero.



Si encuentra el usuario devuelve sus datos y si no existe devuelve que no hay informacion.



Lo importante es que tanto REST como gRPC usan esta misma funcion. No hice una busqueda diferente para cada uno.



2\. REST



Para la parte de REST use Flask.



El servidor recibe una peticion con el ID del usuario, por ejemplo:



http://localhost:5000/usuario/1



REST toma ese ID y manda llamar a la funcion `buscar\_usuario`.



Si encuentra el usuario devuelve la informacion en formato JSON.



REST trabaja en el puerto 5000.



3\. gRPC



Para gRPC primero hice el archivo `servicio.proto`.



En este archivo se define que informacion manda el cliente y que informacion debe regresar el servidor.



En este caso el cliente manda el ID y el servidor puede regresar:



\- id

\- nombre

\- carrera

\- si el usuario fue encontrado



Despues genere los archivos de Python necesarios a partir del archivo `.proto`.



gRPC tambien usa la funcion `buscar\_usuario`, igual que REST.



gRPC trabaja en el puerto 50051.



4\. Diferencia entre REST y gRPC



Lo que entendi es que la logica del servicio no cambia.



En los dos casos el programa busca exactamente al mismo usuario.



Lo que cambia es la forma en la que se comunican el cliente y el servidor.



REST usa HTTP y la respuesta se maneja como JSON.



gRPC usa el contrato definido en `servicio.proto` y Protocol Buffers para mandar los datos.



5\. Cliente



Tambien hice un cliente que prueba las dos formas de comunicacion.



El cliente primero hace la consulta por REST y despues hace la misma consulta por gRPC.



Use el mismo ID en las dos pruebas para poder comprobar que los dos regresaran la misma informacion.



Con el ID 1 los dos devolvieron:



Bali Yandrei - Ingenieria



6\. Docker



El servidor y el cliente tienen su propio Dockerfile.



Tambien hice un archivo `docker-compose.yml` para levantar los dos servicios y permitir que se comuniquen dentro de la misma red de Docker.



Para iniciar el proyecto use:



docker compose up --build



Dentro de Docker el cliente se conecta al servidor usando el nombre `server`.



7\. Resultado



Al final pude comprobar que el mismo servicio funciona por REST y por gRPC.



Las dos interfaces regresaron la misma informacion porque las dos usan la misma logica de `servicio.py`.



Con esta tarea entendi mejor que REST y gRPC pueden ser formas diferentes de comunicarse con un servicio sin tener que repetir la logica principal del programa.

