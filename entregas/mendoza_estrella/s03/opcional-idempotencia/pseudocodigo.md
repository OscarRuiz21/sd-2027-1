# Pseudocódigo Map vs UNIQUE

## VERSION CON MAP

mapa = {}

función cobrar(llave, monto):
    si llave existe en mapa:
        devolver mapa[llave]
    resultado = procesarCobro(monto)
    mapa[llave] = resultado
    devolver resultado

Entre revisar si existe y guardar hay un hueco de tiempo.
Si dos peticiones llegan casi juntas, ninguna ve la otra a tiempo y las dos cobran.


## VERSION CON UNIQUE

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

Funciona porque el INSERT con UNIQUE es una sola operación, la base de datos garantiza que solo una petición puede insertar la misma llave.
