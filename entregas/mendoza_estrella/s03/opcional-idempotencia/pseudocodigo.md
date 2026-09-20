<<<<<<< HEAD
## Pseudocódigo Map vs UNIQUE

# VERSION CON MAP

=======
# Pseudocódigo Map vs UNIQUE

## VERSION CON MAP

```
>>>>>>> 8408d002f7050d953bff04ebe74af92a67551f89
mapa = {}

función cobrar(llave, monto):
    si llave existe en mapa:
        devolver mapa[llave]
    resultado = procesarCobro(monto)
    mapa[llave] = resultado
    devolver resultado
<<<<<<< HEAD
=======
```
>>>>>>> 8408d002f7050d953bff04ebe74af92a67551f89

Entre revisar si existe y guardar hay un hueco de tiempo.
Si dos peticiones llegan casi juntas, ninguna ve la otra a tiempo y las dos cobran.


<<<<<<< HEAD
# VERSION CON UNIQUE

=======
## VERSION CON UNIQUE

```
>>>>>>> 8408d002f7050d953bff04ebe74af92a67551f89
función cobrar(llave, monto):
    intentar INSERT INTO tabla (llave, estado='pending')

    si el INSERT funcionó:
        resultado = procesarCobro(monto)
        UPDATE tabla SET estado='completed', respuesta=resultado
        devolver resultado

    si el INSERT falló (llave duplicada):
        buscar estado guardado de esa llave
        si ya estaba completed: devolver el mismo resultado de antes
        si seguía pending: decir "espera, se está procesando"
<<<<<<< HEAD

Funciona porque el INSERT con UNIQUE es una sola operación, la base de datos garantiza que solo una petición puede insertar la misma llave.
=======
```
Funciona porque el INSERT con UNIQUE es una sola operación, la base de datos garantiza que solo una petición puede insertar la misma llave.
>>>>>>> 8408d002f7050d953bff04ebe74af92a67551f89
