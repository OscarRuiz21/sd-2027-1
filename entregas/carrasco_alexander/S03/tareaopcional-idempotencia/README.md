\# Opcional S03 — Implementa bien el patrón de idempotency key



\## 1. ¿Qué es una Idempotency-Key?

Una Idempotency-Key es básicamente un identificador único que le dice al servidor "oye, esta operación ya la intenté antes, no la hagas dos veces".



El caso clásico es un cobro: si el cliente manda una petición, algo falla en la conexión y la vuelve a mandar, sin este mecanismo el servidor cobraría dos veces. La llave evita eso.



\---



\## 2. Implementación ingenua utilizando un Map



Una implementación sencilla podría guardar las llaves utilizadas en un `Map` en memoria.



El pseudocódigo sería:



```text

recibir petición con Idempotency-Key



si el Map contiene la llave:

&#x20;   devolver el resultado anterior

si no:

&#x20;   realizar el cobro

&#x20;   guardar la llave en el Map

&#x20;   devolver el resultado

```



Por ejemplo:



```text

Map:

ABC123 → cobro realizado



Nueva petición:

ABC123



La llave ya existe

→ no realizar otro cobro

→ devolver el resultado anterior

```



A primera vista parece que funciona. Y funciona... cuando las peticiones llegan de una en una.





\---



\## 3. ¿Por qué falla el Map?



Aquí es cuando aparece el problema, ya que dos peticiones con la misma llave llegan prácticamente al mismo tiempo.



Por ejemplo:



```text

Petición A            |        Petición B

\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_|\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_		      

¿Existe ABC123?       |

&#x20;     ↓               |

&#x20;    NO               |

&#x20;                     |

&#x20;                     |       ¿Existe ABC123?

&#x20;                     |             ↓

&#x20;                     |            NO

&#x20;                     |

Cobrar $100           |

&#x20;                     |

&#x20;                     |       Cobrar $100

&#x20;                     |

Guardar ABC123        |

&#x20;                     |

&#x20;                     |       Guardar ABC123

```



Las dos peticiones revisaron si existía la llave antes de que alguna la guardara. Entonces las dos pasan, las dos cobran, y el resultado es exactamente lo que queríamos evitar: un cobro duplicado.



Esto tiene nombre: TOCTOU (Time Of Check To Time Of Use). El problema es que entre el momento en que reviso si la llave existe y el momento en que actúo, puede colarse otra petición. El Map no puede evitar eso porque la revisión y el guardado son dos pasos separados.



Otro problema adicional: si el servidor se reinicia, el Map desaparece. Toda la memoria se pierde y las llaves con ella.



\---



\## 4. Implementación utilizando UNIQUE



Una solución más segura es utilizar una base de datos y crear una tabla donde la columna `idempotency\_key` tenga una restricción `UNIQUE`.



Por ejemplo:



```sql

CREATE TABLE idempotency\_keys (

&#x20;   id INTEGER PRIMARY KEY,

&#x20;   idempotency\_key VARCHAR(255) UNIQUE,

&#x20;   status VARCHAR(20),

&#x20;   result TEXT

);

```



La parte importante es:



```text

idempotency\_key UNIQUE

```



Esto significa que la base de datos no permitirá que existan dos registros con la misma llave.



La lógica sería:



```text

recibir petición con Idempotency-Key



intentar insertar la llave



si el INSERT funciona:

&#x20;   realizar el cobro

&#x20;   guardar el resultado

&#x20;   devolver el resultado



si el INSERT falla porque la llave ya existe:

&#x20;   no realizar otro cobro

&#x20;   recuperar el resultado correspondiente

&#x20;   devolverlo

```



\---



\## 5. ¿Diferencias entre Map y UNIQUE?



La diferencia principal es que el `Map` ingenuo separa la comprobación de la acción:



```text

¿Existe?

&#x20;  ↓

NO

&#x20;  ↓

hacer algo

```



Entre esas operaciones puede entrar otra petición.



En cambio, la restricción `UNIQUE` hace que la base de datos sea la encargada de garantizar que una llave solamente pueda registrarse una vez. Si dos peticiones intentan insertar `ABC123` al mismo tiempo, solamente una podrá conseguir el registro y la otra recibirá un error de duplicado.



\---



\## 6. ¿Qué guardamos junto a la llave?



Mientras investigaba noté que no basta con guardar solamente la llave. También necesitamos guardar el resultado de la operación para poder devolverlo si el cliente reintenta:



Por ejemplo:



```text

Idempotency-Key: ABC123

Estado: COMPLETADO

Resultado: Cobro de $100 realizado correctamente

```



De esta manera, si el cliente vuelve a enviar `ABC123`, el servidor puede devolver el resultado anterior en lugar de realizar nuevamente el cobro. También puede ser útil guardar información como el estado de la operación, la respuesta generada y otros datos necesarios para identificar correctamente la operación.



\---



\## 7. ¿Qué pasa si el proceso muere después del INSERT pero antes del cobro?



Este caso es más complicado y fue el que tuve que investigar y pensar más, pero por qué? .



Por ejemplo:



```text

INSERT ABC123

&#x20;     ↓

&#x20;  correcto

&#x20;     ↓

servidor se apaga

&#x20;     ↓

NO se realizó el cobro

```



Si el cliente vuelve a intentar la operación, la base de datos podría indicar que `ABC123` ya existe.



Por eso no es suficiente guardar únicamente la existencia de la llave. También es importante guardar el estado de la operación.



Por ejemplo:



```text

ABC123 → PENDIENTE

```



Después de realizar correctamente el cobro se podría cambiar a:



```text

ABC123 → COMPLETADO

```



De esta manera, el sistema puede distinguir entre una operación que ya terminó y una operación que quedó pendiente debido a una falla.



Este problema requiere de un diseño correcto de las transacciones y la recuperación de operaciones, especialmente cuando existen varios componentes distribuidos.



\---



\## 8. Comparación



| Implementación            | Ventaja                                | Problema                                                                         |

| ------------------------- | -------------------------------------- | -------------------------------------------------------------------------------- |

| `Map` en memoria          | Es sencillo de implementar y rápido    | Puede tener condiciones de carrera y se pierde al reiniciar el proceso           |

| `UNIQUE` en base de datos | La base de datos garantiza la unicidad | Requiere utilizar una base de datos y diseñar correctamente el manejo de estados |



\---



\## Conclusión



Para manejar operaciones entre cliente y servidor de forma segura, vimos dos maneras de implementar el patrón de idempotencia.



La primera fue usando un Map en memoria. La idea es que cada petición pasa por una serie de pasos: se revisa si la llave ya existe, se procesa la operación y se guarda el resultado. Esto funciona bien siempre que las peticiones lleguen una a la vez.



El problema aparece cuando dos peticiones llegan al mismo tiempo. Como son procesos independientes, ambas pueden revisar el Map en el mismo instante, ver que la llave no existe todavía, y las dos continuar con el cobro. El resultado es un cobro duplicado, que era exactamente lo que queríamos evitar.



Para resolver eso usamos una base de datos con una restricción UNIQUE. Esta restricción garantiza que la llave de cada operación no puede repetirse. Entonces si dos peticiones llegan al mismo tiempo intentando insertar la misma llave, la base de datos solo deja pasar una y rechaza la otra. Sin duplicados.



Pero queda un caso más complicado: ¿qué pasa si el servidor se cae justo después de hacer el INSERT pero antes de realizar el cobro? La llave ya existe en la base de datos, pero el cobro nunca ocurrió.



Por eso no basta con guardar solo la llave. También necesitamos guardar el estado de la operación. Por ejemplo:



text

ABC123 → PENDIENTE   (la llave se insertó pero el cobro no ocurrió)

ABC123 → COMPLETADO  (el cobro se realizó correctamente)



Así, si el cliente reintenta y el servidor detecta que la operación quedó en PENDIENTE, sabe que debe continuar con el cobro en lugar de rechazarlo. Y si está COMPLETADO, simplemente devuelve el resultado anterior sin cobrar de nuevo.

