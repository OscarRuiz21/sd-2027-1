Evidencia T01



1. Prueba con Docker



Se levantaron el servidor y el cliente usando Docker Compose.



El comando usado fue:



docker compose up --build



\-Resultado



Respuesta REST:



{'carrera': 'Ingenieria', 'id': 1, 'nombre': 'Bali Yandrei'}



Respuesta gRPC:



{'id': 1, 'nombre': 'Bali Yandrei', 'carrera': 'Ingenieria'}



2\. Docker ps



El servidor quedo corriendo con los puertos:



5000 para REST



50051 para gRPC



3\. Lo que use para revisarlo



docker ps



docker compose logs --no-color



Con los logs pude revisar que el cliente hizo la consulta por REST y por gRPC y en los dos casos recibio la misma informacion.

