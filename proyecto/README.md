# Proyecto final

Cada equipo construye su propio sistema distribuido, sobre un caso que eligió (los equipos y sus
casos están en la [Discussion #44](https://github.com/OscarRuiz21/sd-2027-1/discussions/44)).
Igual que Mexi Banco, empieza como un monolito sencillo y se va partiendo por ramas
(`01-monolito`, `02-separacion`, `03-...`) conforme avanzamos en el curso.

En la defensa no califico la lógica de negocio: le voy a preguntar a **cualquiera** del equipo
dónde vive el gateway, el directorio, el balanceador, cómo manejan las operaciones entre
servicios y qué pasa si algo falla.

## Para el sábado 17 de octubre: el repo del equipo y su monolito

**Entrega por equipo · sábado 17 de octubre, antes de las 07:00**

Ya con el PRD, el equipo abre **su propio repositorio** y escribe la primera versión de su sistema:
un **monolito** sencillo, igual que empezó Mexi Banco.

1. **Un repo por equipo** en GitHub, con el nombre `sd-2027-1-nombre-del-equipo` (minúsculas y
   guiones). Lo crea un integrante y agrega a los demás y a **OscarRuiz21** como colaboradores
   (*Settings → Collaborators*).
2. **Rama `main`** con el `README.md` (qué es el sistema, quiénes son y cómo se levanta) y el PRD en
   `docs/PRD.md`.
3. **Rama `01-monolito`** con el monolito: sus operaciones, la lógica de negocio y los endpoints,
   en una sola aplicación con su base de datos y su `docker-compose.yml`. No tiene que estar
   completo: tiene que correr y tener las operaciones principales del PRD.
4. **El trabajo se reparte entre los cuatro**: en la defensa se nota en el historial de commits.
5. Cuando esté, **respondan a su comentario en la [Discussion #44](https://github.com/OscarRuiz21/sd-2027-1/discussions/44)**
   con la liga al repo.

Si su equipo no usa Java, está bien: al final van a tener que explicar cómo resolvieron en su
tecnología el gateway, el directorio, el balanceo, los datos y el manejo de fallas.

## El PRD (ya entregado)

**Entrega por equipo · sábado 10 de octubre, antes de las 07:00**

El **PRD** (*Product Requirements Document*) dice qué va a hacer su sistema, para quién, qué
problema resuelve y qué decisiones tomaron. Lo hacen **los cuatro juntos** con ayuda de un
agente de IA y la skill [`metodo-socratico`](skills/metodo-socratico/SKILL.md): el agente les
hace una pregunta a la vez hasta que el equipo tenga claro qué va a construir y por qué.

Cómo usar las skills: [`skills/README.md`](skills/README.md).

**Dónde va.** **Un solo integrante** lo sube, en su rama de entregas:

```
proyecto/nombre-del-equipo/PRD.md
```

```bash
git checkout entregas_apellido_nombre
git pull origin main
mkdir -p proyecto/nombre-del-equipo
# ... copia aquí el docs/PRD.md que generaron ...
git add proyecto/nombre-del-equipo
git commit -m "Proyecto: PRD del equipo nombre-del-equipo"
git push
```

Usen el nombre del equipo en minúsculas y con guiones (por ejemplo `the-four-nodes`). Después,
**respondan a su propio comentario en la Discussion #44** con la liga al archivo, para que sepa
en qué rama quedó.

**No abras pull request**: el push ES la entrega.
