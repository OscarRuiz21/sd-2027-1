# La rutina de entrega

Cada sábado, siempre igual. **Una sola rama tuya para todo el semestre.**

## Primera vez · conecta tu clon con TU rama del semestre

Tu rama `entregas_apellido_nombre` **ya existe en GitHub** (te la creamos desde tu rama
del lab, con tu trabajo adentro). Elige UN camino según tu caso:

**A · Lo más común — solo tráela:**

```bash
git fetch origin                          # entérate de las ramas nuevas del repositorio
git checkout entregas_apellido_nombre     # crea tu copia local, ya conectada con la remota
```

**B · Ya tenías una rama local con otro nombre (s02-apellido, etc.) y quieres conservarla renombrándola:**

```bash
git branch -m s02-apellido entregas_apellido_nombre   # renómbrala (-m = move/rename)
git fetch origin                                      # entérate de la rama remota
git branch -u origin/entregas_apellido_nombre         # conéctala con la remota (-u = upstream)
git pull                                              # mezcla lo que ya haya en la remota con lo tuyo
git push                                              # y sube el resultado
```

**C · Prefieres empezar de cero:** haz el camino A, copia tus archivos a tu carpeta de
`entregas/apellido_nombre/`, y sigue la rutina normal de abajo.

Cualquiera de los tres se hace **una sola vez**; después, tu semana es la rutina de abajo.

## Mantén tu local al día

Son **dos pulls distintos, para dos cosas distintas**:

```bash
git pull                    # trae TU rama tal como está en GitHub (si trabajas en dos máquinas, o si te subimos algo)
git pull origin main        # trae el material nuevo del curso (guías, labs, lecturas) a tu rama
```

## 0 · Párate en tu rama

```bash
cd sd-2027-1
git checkout entregas_apellido_nombre    # tu rama; si no existe: git checkout -b entregas_apellido_nombre
git pull                                  # trae tu última versión (por si trabajaste en otra máquina)
```

**Nunca trabajas en `main`**: está protegida y no te va a dejar subir.

## 1 · (Opcional) trae el material nuevo del curso a tu rama

```bash
git pull origin main      # trae guías y material nuevos DIRECTO a tu rama, en un paso
```

No te asustes si te lista ramas de tus compañeros: es solo el índice, no toca tu trabajo.
Y el merge no genera conflictos porque tú solo tocas tu carpeta. Basta hacerlo al empezar
cada práctica.

## 2 · Trabaja

Los **labs** van en `entregas/apellido_nombre/sNN/` y las **tareas** en
`entregas/apellido_nombre/tareas/tNN/`. Nunca toques la carpeta de otro ni el
material del curso.

## 3 · Guarda

```bash
git status
git add entregas/apellido_nombre/sNN      # o tareas/tNN si es una tarea
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
