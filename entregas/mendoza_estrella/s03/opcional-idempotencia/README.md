IDEMPOTENCY KEY - por qué el Map falla y el UNIQUE no

Problema: Si un cliente no sabe si su cobro pasó o no, puede reintentar y cobrar dos veces por error.

El MAP falla porque separa revisar y guardar en dos pasos.
Si dos peticiones llegan casi juntas ninguna ve el trabajo de la otra y ambas cobran.

El UNIQUE funciona porque convierte revisar y guardar en una sola operación. 
La base de datos asegura que solo una petición puede insertar la misma llave, sin huecos de tiempo.

PREGUNTAS:

¿Qué guardas junto a la llave para poder devolver el resultado original en el reintento?
El resultado completo del cobro (id, monto), para devolver lo mismo si el cliente reintenta.

¿Cuándo expira una llave?
Debería de ser un tiempo considerable, ni muy pronto porque perdería protección en reintentos tardíos, ni nunca o la tabla crecerá para siempre. 
Un valor razonable: 24 horas.

¿Qué pasa si el proceso muere DESPUÉS del INSERT pero ANTES de cobrar?
La llave queda atascada en pending para siempre.
Posteriormente, si se realiza un proceso de limpieza, puede marcarse como failed después de cierto tiempo para permitir un reintento real.