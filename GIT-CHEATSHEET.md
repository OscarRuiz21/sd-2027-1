# La rutina de entrega

Cada sábado, siempre igual. **Una sola rama tuya para todo el semestre.**

## 0 · Párate en tu rama

```bash
cd sd-2027-1
git checkout entregas_apellido_nombre    # tu rama; si no existe: git checkout -b entregas_apellido_nombre
git pull                                  # trae tu última versión (por si trabajaste en otra máquina)
```

**Nunca trabajas en `main`**: está protegida y no te va a dejar subir.

## 1 · (Opcional) trae el material nuevo del curso a tu rama

```bash
git fetch origin          # entérate de lo nuevo en el repositorio
git merge origin/main     # trae guías y material nuevos a tu rama (no genera conflictos: tú solo tocas tu carpeta)
```

## 2 · Trabaja

Tu trabajo va en `entregas/apellido_nombre/sNN/`. Nunca toques la carpeta de otro ni el
material del curso.

## 3 · Guarda

```bash
git status
git add entregas/apellido_nombre/sNN
git commit -m "S03: mis dos servicios sobre el sistema del curso"
```

Nombra tu carpeta en el `add`, no uses `git add .`. El mensaje empieza con el número de
sesión.

## 4 · Sube — y eso ES la entrega

```bash
git push -u origin entregas_apellido_nombre
```

El `-u origin …` solo la primera vez; después basta `git push`. **La hora del último push
define si tu entrega queda en tiempo (hasta el domingo) o tarde** — tarde no baja puntos,
pero se registra para tu tendencia (ver `ENTREGAS.md`). **NO abras pull request**: el PR
es UNO solo, al final del curso, con todo tu trabajo.

## Los errores de siempre

| Lo que pasa | Qué haces |
|---|---|
| `protected branch` al hacer `push` | Commiteaste en `main` sin querer. `git checkout -b entregas_apellido_nombre` se lleva tus commits a tu rama y desde ahí pusheas |
| Hiciste `commit` pero no aparece en GitHub | Falta el `push` |
| Trabajaste en tu rama vieja (`s02-…`) | Tu rama nueva `entregas_…` ya existe con todo lo tuyo: `git fetch origin` y `git checkout entregas_apellido_nombre`; desde hoy todo va ahí |
| Olvidaste el `-u origin …` | Git te imprime el comando exacto: cópialo y córrelo |
| Te pide contraseña y la rechaza | La contraseña de GitHub no sirve para push: en Windows autoriza en el navegador; en macOS/Linux usa un token (Settings → Developer settings → Tokens classic, permiso `repo`) |

**Si te atoras:** no borres nada ni vuelvas a clonar. `git status`, captura, y me la mandas.
