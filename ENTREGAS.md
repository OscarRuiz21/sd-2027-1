# Política de entregas

## El modelo: una rama tuya para todo el semestre

Trabajas SIEMPRE en **tu rama** `entregas_apellido_nombre`. Ahí subes con push cada lab y
cada tarea, dentro de tu carpeta `entregas/apellido_nombre/`. **No se abren pull requests
durante el semestre**: al final del curso cada quien abre UN PR con todo su trabajo y ese
es el que se integra a `main`.

```
entregas/apellido_nombre/
├── p00/        ← la prueba de git
├── s02/        ← lab de la sesión 2 (Docker)
└── s03/ … s12/
```

## El acantilado

El incentivo es terminar en clase, porque ahí estoy yo y ahí se aprende más. Se califica
solo con el timestamp del último push de esa sesión a tu rama; yo no adjudico nada.

| Cuándo pusheas | Sobre |
|---|---|
| **Durante la clase** | **10** |
| Mismo sábado, hasta las 23:59 | **9** |
| Domingo | **7** |
| Lunes o martes | **6** |
| Después del martes | No se recibe |

## El código cierra con la clase, la reflexión no

**El código** se entrega dentro del horario y aplica la tabla de arriba.

**Tu `evidencia.md`** (qué se rompió, qué gané y qué pagué) la puedes seguir editando
**hasta el martes sin penalización**. Pensar bien eso no se hace con prisa, y es la parte
que de verdad me interesa. Solo cuentan como "entrega tarde" los pushes que tocan el
código; pulir la evidencia no.

## La ficha de rescate

Tienes **un pase libre por semestre**: una entrega tarde que cuenta como puntual, sin
explicar por qué. Lo declaras poniendo `[rescate]` en el mensaje del commit de esa
entrega, y se acabó. Cubre la laptop muerta, la gripa y la emergencia, sin que yo tenga
que juzgar excusas.

## Peer review

Con el modelo de rama única, la revisión entre pares semanal por PR queda **pausada**:
vuelve sobre el **PR final del curso**, que necesitará la aprobación de un compañero con
al menos un comentario sustantivo antes de integrarse a `main`.
