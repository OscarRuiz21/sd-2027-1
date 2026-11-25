# S03 opcional · Clave de idempotencia

Josué Hernández Rubio · Sistemas Distribuidos · Grupo 2

El servicio simula un cobro con `POST /cobros`. Recibe un `monto_centavos` y la cabecera `Idempotency-Key`. El cobro consiste en guardar un registro en SQLite.

Si primero pregunto a un `Map` si tiene la clave y luego la guardo, dos solicitudes pueden pasar la consulta antes de que alguna haya guardado la clave. Por eso el servicio intenta insertar la clave directamente en una columna con `UNIQUE`. Guarda la clave, el monto y el cobro en la misma transacción. Si el proceso se interrumpe antes de confirmar la transacción, SQLite revierte ambas inserciones.

La solicitud que inserta la clave devuelve **201**. Si la otra usa la misma clave y el mismo monto, devuelve **200** con el mismo número de cobro. Si intenta usar la clave con otro monto, devuelve **409**. En esta práctica las claves se conservan mientras exista la base.

## Prueba simultánea

Desde esta carpeta, con Python 3:

```bash
python3 -B prueba.py
```

La prueba levanta el servidor con una base temporal y abre dos conexiones HTTP. Una barrera libera las dos solicitudes juntas. Al final, consulta las tablas para verificar que solo se guardó un cobro.

```text
Dos solicitudes con Idempotency-Key: pedido-17
Solicitud 1 | HTTP 200 | {"cobro_id": 1, "monto_centavos": 12500}
Solicitud 2 | HTTP 201 | {"cobro_id": 1, "monto_centavos": 12500}
SQLite | solicitudes: 1 | cobros: 1
```

Cualquiera de las dos solicitudes puede obtener el 201. Para levantar el servicio manualmente se puede ejecutar `python3 servicio.py`.

## Fuentes

- SQLite. [UPSERT](https://www.sqlite.org/lang_upsert.html) y [transacciones](https://www.sqlite.org/lang_transaction.html).
