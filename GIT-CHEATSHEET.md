# La rutina de entrega

Cada sábado, siempre igual.

## 0 · Trae lo nuevo

```bash
cd sd-2027-1
git checkout main
git pull
```

## 1 · Crea tu rama de la sesión

```bash
git checkout -b sNN-apellido      # ejemplo: s02-ramirez
```

**Nunca trabajas en `main`**: está protegida y no te va a dejar subir.

## 2 · Trabaja

Tu trabajo va en `entregas/apellido_nombre/sNN/`. Nunca toques la carpeta de otro ni el
material del curso.

## 3 · Guarda

```bash
git status
git add entregas/apellido_nombre/sNN
git commit -m "S02: primer contenedor e imagen propia"
```

Nombra tu carpeta en el `add`, no uses `git add .`. El mensaje empieza con el número de
sesión.

## 4 · Sube tu rama

```bash
git push -u origin sNN-apellido
```

El `-u origin …` solo la primera vez de cada rama; después basta `git push`. **La hora del
último push es la que califica.**

## 5 · Abre tu pull request

En GitHub: **Compare & pull request** → título `SNN · Apellido` → llena la **plantilla**
(qué hice · qué se rompió al quitarlo · qué gané y qué pagué · qué haría a 10x) →
**Create pull request**.

La plantilla no es trámite: **la descripción es la mitad de la entrega**. El código cierra
con la clase; la descripción la puedes pulir hasta el martes sin penalización.

## 6 · Peer review

Pide la revisión a tu compañero asignado (rota cada semana). Tu PR necesita **su
aprobación con al menos un comentario sustantivo** antes de que yo lo revise e integre.
Cuando te toque revisar a ti: comenta el razonamiento, no las comas. Tus revisiones
también se califican.

## Los errores de siempre

| Lo que pasa | Qué haces |
|---|---|
| `protected branch` al hacer `push` | Commiteaste en `main` sin querer. `git checkout -b sNN-apellido` se lleva tus commits a la rama nueva y desde ahí pusheas |
| Hiciste `commit` pero no aparece en GitHub | Falta el `push` |
| Olvidaste el `-u origin …` | Git te imprime el comando exacto: cópialo y córrelo |
| Te pide contraseña y la rechaza | La contraseña de GitHub no sirve para push: en Windows autoriza en el navegador; en macOS/Linux usa un token (Settings → Developer settings → Tokens classic, permiso `repo`) |

**Si te atoras:** no borres nada ni vuelvas a clonar. `git status`, captura, y me la mandas.
