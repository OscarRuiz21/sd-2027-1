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

## Los tiempos

**Labs de la semana** (la hora es la de tu último push a esa carpeta):

| Cuándo pusheas | Cómo se registra |
|---|---|
| Hasta el **domingo** 23:59 | **En tiempo y forma** |
| De lunes a sábado, antes de la siguiente sesión | **Tarde** |
| Después de la siguiente sesión | Ya no se registra para esa semana |

**Tarde NO baja puntos.** Se registra solo para ver tu **tendencia de entregas**, y esa
tendencia se toma en cuenta al asignar los **puntos extra** del final del curso.

**Lecturas** (2 preguntas + 1 hallazgo en la Discussion de la semana):

| Cuándo comentas | Cómo se registra |
|---|---|
| Hasta el **sábado antes de las 07:00** (cuando abre la teoría) | **En tiempo** |
| Hasta el **domingo**, todo el día | **Tarde** (no penaliza; queda en la tendencia) |

## Peer review

Con el modelo de rama única, la revisión entre pares semanal queda **pausada**: vuelve
sobre el **PR final del curso**, que necesitará la aprobación de un compañero con al menos
un comentario sustantivo antes de integrarse a `main`.
