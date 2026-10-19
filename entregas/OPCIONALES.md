# Tareas opcionales

Ninguna es obligatoria y **no afectan tu calificación**. Cuentan para tu **tendencia de
entregas** y se consideran en los **puntos extra** del final del curso. Se entregan igual
que todo: **push a tu rama** `entregas_apellido_nombre`, en la carpeta que indique cada
tarea.

---

## Opcional S03 · Implementa bien el patrón de *idempotency key*

**El gancho.** La implementación ingenua del patrón que vimos en clase — un `Map` en
memoria donde revisas "¿ya existe esta llave?" y, si no, procesas y la guardas — **tiene
una carrera**: entre el *check* y el *use* (**TOCTOU**, *time-of-check to time-of-use*)
pueden colarse dos peticiones con la misma llave al mismo tiempo… y cobrar doble, que era
exactamente lo que el patrón quería evitar.

**La idea correcta.** La unicidad se apoya en quien sí sabe de atomicidad: una restricción
**`UNIQUE`** en la base de datos. Insertas la llave **antes** de procesar; si el `INSERT`
falla por duplicado, otra petición ya está procesando (o procesó) esa operación — y
respondes en consecuencia, sin ejecutar dos veces.

**Qué entregar** (elige tu nivel):

1. **Nivel completo:** un mini servicio (el lenguaje que quieras) con un endpoint de
   "cobro" idempotente vía header `Idempotency-Key`, respaldado por una tabla con
   `UNIQUE`. Incluye cómo probar la carrera (dos peticiones simultáneas con la misma
   llave) y qué responde cada una.
2. **Nivel ensayo:** el pseudocódigo de ambas versiones (la del `Map` y la correcta) más
   un `README.md` explicando con tus palabras **por qué el `Map` falla y el `UNIQUE` no**.

**Dónde va:**

```
entregas/apellido_nombre/s03/opcional-idempotencia/
```

**Para pensar mientras lo haces:** ¿qué guardas junto a la llave para poder devolver el
resultado original en el reintento? ¿cuándo expira una llave? ¿qué pasa si el proceso
muere DESPUÉS del `INSERT` pero ANTES de cobrar?

Es el tipo de pregunta que cae en entrevistas de backend. Vale la pena pelearse con ella.
