# Empieza aquí

**Sistemas Distribuidos · Clave 1959 · Grupo 2 · FI-UNAM**
Prof. Oscar Manuel Ruiz Hurtado · Semestre 2027-1

Este es el repositorio del curso. Aquí subo yo el material y las guías de laboratorio, aquí
entregas tú los labs, y aquí viven las lecturas (en **Discussions**). Todos trabajamos **en
este mismo repositorio**, como un equipo en una empresa.

## Tus cuatro primeros pasos

1. **Mándame tu usuario de GitHub** (formato de una línea, como la encuesta de la clase 1:
   `usuario: fulanito123`). Te llega una **invitación de colaborador**: acéptala en tu
   correo o en `github.com/notifications`. Sin eso no puedes subir nada.
2. **Clona el repositorio** (el mismo para todos, sin fork):
   ```bash
   git clone https://github.com/OscarRuiz21/sd-2027-1.git
   ```
3. **Instala Docker antes del sábado 29** con la
   [guía por sistema operativo](https://oscarruiz21.github.io/sd-2027-1/labs/Instala-Docker.html).
   Termina con `docker run hello-world`; si lo ves, ya quedaste.
4. **La Lectura 1, opcional pero recomendada**, en la pestaña
   [Discussions](https://github.com/OscarRuiz21/sd-2027-1/discussions): si participas,
   el formato es 2 preguntas + 1 hallazgo, de preferencia antes del jueves para que
   alcancen a votarse. No necesitas ser colaborador, solo tu cuenta.

## Cómo funciona el repositorio

```
sd-2027-1/
├── material/        ← decks y material de cada sesión (lo subo yo)
├── labs/            ← las guías de laboratorio (las subo yo)
├── lecturas/        ← el programa de lecturas y sus enlaces
└── entregas/        ← aquí va tu trabajo, en tu carpeta
    └── apellido_nombre/
        ├── s02/
        └── s03/ … s12/
```

Cinco reglas:

1. **Nunca trabajas en `main`**: está protegida. Cada sesión trabajas en una rama
   `sNN-apellido` y entregas con un **pull request**.
2. **El PR lleva plantilla** (se llena sola al abrirlo). La descripción es parte de la
   entrega: ahí va tu razonamiento, no un trámite.
3. **Tu PR necesita la aprobación de un compañero** antes de que yo lo revise, con al menos
   un comentario sustantivo. GitHub no me deja integrarlo sin esa aprobación. Revisar
   enseña tanto como escribir, y también se califica la calidad de tus revisiones.
4. **Cada quien toca solo su carpeta** de `entregas/`.
5. **La hora que cuenta es la del último push a tu rama.** La política completa de
   puntualidad, con la ficha de rescate, está en [`ENTREGAS.md`](ENTREGAS.md).

## La rutina de cada sábado

Está paso a paso en [`GIT-CHEATSHEET.md`](GIT-CHEATSHEET.md).

## Dudas

Técnicas: abre un **Issue** con captura. De lecturas: en la propia Discussion. Entre
semana es gratis; el sábado a las 07:05 cuesta la sesión.
