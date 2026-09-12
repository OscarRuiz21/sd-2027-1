# Entregas

Una carpeta por persona, con tu **primer apellido y tu primer nombre, en minúsculas y sin
acentos, unidos por un guion bajo**:

```
entregas/
└── ramirez_ana/
    ├── p00/
    ├── s02/
    └── s03/ … s12/
```

Todo se entrega con **push a tu rama** `entregas_apellido_nombre` — sin pull request hasta
el final del curso (la rutina completa está en el
[`GIT-CHEATSHEET.md`](../GIT-CHEATSHEET.md); los tiempos, en
[`ENTREGAS.md`](../ENTREGAS.md)). Cada quien toca solo su carpeta.

## Las tareas

Aquí se van apilando las **tareas**, la más reciente hasta arriba. Las **prácticas de
laboratorio** no van aquí: cada una tiene su guía en [`labs/`](../labs/).

### Caso de estudio · el mismo servicio por REST y por gRPC

**Asignada en la S4 (12-sep) · entrega el domingo 20 de septiembre, 23:59**

Escribes **un** servicio y lo expones **dos veces**: por REST y por gRPC. La lógica de negocio
es la misma; lo único que cambia es el controlador y el mecanismo de comunicación. El servicio
puede ser trivial: recibe un ID y devuelve la información asociada.

Se entrega en `entregas/apellido_nombre/s04/rest-vs-grpc/` y va con cliente, servidor, tu
`.proto`, Dockerfile de cada uno, la red o el compose que los conecte, evidencia de que corre
y un `README.md` con tu diseño. **Punto extra** si mides los bytes que viajan por cada uno.

**Las instrucciones completas están en [`../tareas/T01-REST-vs-gRPC.md`](../tareas/T01-REST-vs-gRPC.md).**

## Tareas opcionales

Las **opcionales** no son obligatorias y no afectan tu calificación: cuentan para tu tendencia
de entregas y para los **puntos extra** del final del curso. Viven en
**[`OPCIONALES.md`](OPCIONALES.md)**. Está publicada la primera: **implementar bien el patrón
de idempotency key** (S03).
