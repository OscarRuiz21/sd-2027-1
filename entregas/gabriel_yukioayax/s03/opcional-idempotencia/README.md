¿Por qué el Map falla y el UNIQUE no?
Un diccionario en memoria sufre de una condición de carrera llamada TOCTOU (Time of check to time of use), si dos peticiones llegan al mismo milisegundo, ambas evalúan que la llave no existe y procesan el cobro doble.
Con UNIQUE, la base de datos garantiza la atomicidad, el primer INSERT funciona y bloquea la llave; el segundo falla inmediatamente con un error de integridad.
