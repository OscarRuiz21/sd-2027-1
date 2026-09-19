

## 1. `docker compose ps`

> Ambos servicios, `app` y `db`, aparecen como **`healthy`**, no solo `running`.

```text
PS C:\Users\PC\Desktop\MexiBanco\mexi-banco> docker compose ps
NAME               IMAGE                COMMAND                  SERVICE   CREATED         STATUS                    PORTS
mexi-banco-app-1   mexi-banco-app       "java -jar app.jar"      app       7 minutes ago   Up 14 seconds (healthy)   0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
mexi-banco-db-1    postgres:17-alpine   "docker-entrypoint.s…"   db        7 minutes ago   Up 7 minutes (healthy)    5432/tcp
```

---

## 2. Transferencia de saldo interna

> Cuenta 1 inicia con **1000** y cuenta 2 con **500**. Tras transferir **200**, quedan en **800** y **700**; la suma total sigue siendo 1500.

```bash
PC@YUICHI MINGW64 ~/Desktop/MexiBanco/mexi-banco ((v05))
$ curl localhost:8080/cuentas/002180000000000001
{"id":1,"clabe":"002180000000000001","titular":"Tu nombre","saldo":1000.00}
PC@YUICHI MINGW64 ~/Desktop/MexiBanco/mexi-banco ((v05))
$ curl -X POST localhost:8080/cuentas -H "Content-Type: application/json" \
  -d '{"clabe":"002180000000000002","titular":"Tu nombre 2","saldoInicial":500}'
{"id":2,"clabe":"002180000000000002","titular":"Tu nombre 2","saldo":500}
PC@YUICHI MINGW64 ~/Desktop/MexiBanco/mexi-banco ((v05))
$ curl -X POST localhost:8080/transferencias -H "Content-Type: application/json" \
  -d '{"claveOrigen":"002180000000000001","claveDestino":"002180000000000002","monto":200}'

PC@YUICHI MINGW64 ~/Desktop/MexiBanco/mexi-banco ((v05))
$ curl localhost:8080/cuentas/002180000000000001
{"id":1,"clabe":"002180000000000001","titular":"Tu nombre","saldo":800.00}
PC@YUICHI MINGW64 ~/Desktop/MexiBanco/mexi-banco ((v05))
$ curl localhost:8080/cuentas/002180000000000002
{"id":2,"clabe":"002180000000000002","titular":"Tu nombre 2","saldo":700.00}
```

---

## 3. Matando la app para verificar lo que Compose no hace

> Después de `docker compose stop app`, la app deja de responder y **no vuelve sola**.

```text
PS C:\Users\PC\Desktop\MexiBanco\mexi-banco> docker compose stop app
[+] stop 1/1
 ✔ Container mexi-banco-app-1 Stopped                                                                              0.3s
```

```bash
PC@YUICHI MINGW64 ~/Desktop/MexiBanco/mexi-banco ((v05))
$ curl localhost:8080/cuentas/002180000000000001
curl: (7) Failed to connect to localhost port 8080 after 2253 ms: Could not connect to server
```

### Aquí lo revivimos y verificamos que no se perdieron datos

> Al levantar `app` de nuevo, los saldos siguen en **800** y **700**. El volumen de `db` no se tocó.

```text
PS C:\Users\PC\Desktop\MexiBanco\mexi-banco> docker compose start app
[+] start 2/2
 ✔ Container mexi-banco-db-1  Healthy                                                                              0.5s
 ✔ Container mexi-banco-app-1 Started                                                                              0.1s
```

```bash
PC@YUICHI MINGW64 ~/Desktop/MexiBanco/mexi-banco ((v05))
$ curl localhost:8080/cuentas/002180000000000001
{"id":1,"clabe":"002180000000000001","titular":"Tu nombre","saldo":800.00}
PC@YUICHI MINGW64 ~/Desktop/MexiBanco/mexi-banco ((v05))
$ curl localhost:8080/cuentas/002180000000000002
{"id":2,"clabe":"002180000000000002","titular":"Tu nombre 2","saldo":700.00}
```

---

## 4. Idempotencia SPEI

> Se envió **la misma petición dos veces** con la misma `Idempotency-Key: prueba-001`. Ambas respuestas devuelven el mismo SPEI con `"id":1`, y el saldo pasó de **800 a 750**, así que se descontó **una sola vez**.

### Primer intento

```bash
PC@YUICHI MINGW64 ~/Desktop/MexiBanco/mexi-banco ((v05))
$ curl -X POST localhost:8080/spei -H "Content-Type: application/json" -H "Idempotency-Key: prueba-001" \
  -d '{"claveOrigen":"002180000000000001","bancoDestino":"BBVA","claveDestino":"012180000000000099","monto":50}'
{"id":1,"idempotencyKey":"prueba-001","claveOrigen":"002180000000000001","bancoDestino":"BBVA","claveDestino":"012180000000000099","monto":50,"estado":"ENVIADO"}
```

### Segundo intento, misma llave

```bash
PC@YUICHI MINGW64 ~/Desktop/MexiBanco/mexi-banco ((v05))
$ curl -X POST localhost:8080/spei -H "Content-Type: application/json" -H "Idempotency-Key: prueba-001"   -d '{"claveOrigen":"002180000000000001","bancoDestino":"BBVA","claveDestino":"012180000000000099","monto":50}'
{"id":1,"idempotencyKey":"prueba-001","claveOrigen":"002180000000000001","bancoDestino":"BBVA","claveDestino":"012180000000000099","monto":50.00,"estado":"ENVIADO"}
```

### Saldo final

```bash
PC@YUICHI MINGW64 ~/Desktop/MexiBanco/mexi-banco ((v05))
$ curl localhost:8080/cuentas/002180000000000001
{"id":1,"clabe":"002180000000000001","titular":"Tu nombre","saldo":750.00}
```

---

## Preguntas de cierre

### 1. ¿Qué te dijo Compose que ya sabías, y qué te sorprendió?

En lo personal, ya sabía que Docker sirve para empaquetar una aplicación con todo lo que necesita y que con un solo comando se puede levantar un sistema completo, y lo confirmé al ver que con `docker compose up --build` se construyó la imagen y arrancaron la base de datos y la aplicación juntas. Lo que sí me sorprendió fue que `app` pudiera encontrar a `db` usando solo su nombre, sin que yo escribiera ninguna IP, ya que al inspeccionar la red vi que Docker asignó las direcciones por su cuenta. También me llamó la atención la diferencia entre `running` y `healthy`, porque yo pensaba que si el contenedor estaba corriendo ya estaba listo, pero la aplicación puede seguir arrancando y todavía no responder, antes de empezar me topé con un error porque el archivo `mvnw` tenía saltos de línea de Windows y dentro del contenedor Linux no se podía ejecutar, lo que me hizo ver que el contenedor no depende de mi sistema operativo, sino que corre en su propio entorno.

### 2. ¿Qué tendrías que agregar para que se reiniciara sola, y qué seguiría sin resolver?

Para que la aplicación se reiniciara sola tendría que agregar la clave `restart` con el valor `always` o `unless-stopped` dentro del servicio `app` en el `docker-compose.yml`, aunque algo que entendí al revisarlo es que, aun con esa política, si yo detengo el contenedor manualmente con `docker compose stop`, Docker no lo vuelve a levantar porque respeta que lo apagué a propósito, y solo actúa cuando el proceso se cae por sí mismo, por ejemplo por un error o si lo mato desde adentro con `docker compose exec app kill 1`. Aun agregándolo, en mi opinión quedarían varios problemas sin resolver. Si la aplicación se queda trabada pero el proceso sigue vivo, Docker no la reinicia aunque el healthcheck diga `unhealthy`, porque para él el contenedor no ha muerto; mientras se reinicia hay un tiempo en el que el banco no responde, porque solo existe una copia y no hay otra que atienda; y si se cae la máquina completa, no hay nadie que levante la aplicación en otro servidor, porque todo vive en un solo host. Por eso concluyo que reiniciar un proceso no es lo mismo que garantizar que el servicio esté disponible.

### 3. ¿Qué sacrificaste al no tener un balanceador, y qué pieza del curso lo resuelve?

Al escalar a tres copias sin balanceador me di cuenta de que en realidad tenía tres aplicaciones separadas en los puertos 8080, 8081 y 8082, y no un solo servicio, ya que cuando mandé las 10 peticiones al 8080 todas las atendió la misma copia mientras las otras dos no hicieron nada. Lo que sacrifiqué fue tener un punto de entrada único, el reparto de la carga y la tolerancia a fallos, porque si la copia del 8080 se cae, el cliente no es redirigido a otra y tendría que saber por su cuenta que existen los otros puertos, la pieza del curso que resuelve justo eso es el Service de Kubernetes, que ofrece una sola dirección estable y reparte las peticiones entre todas las copias disponibles, dejando fuera las que no están sanas.

---

## Evidencia del reto

### Error porque tres contenedores quieren el mismo puerto 8080

```text
PS C:\Users\PC\Desktop\MexiBanco\mexi-banco> docker compose up -d --scale app=3
[+] up 3/4
 ✔ Container mexi-banco-app-1 Running                                                                                0.0s
 ✔ Container mexi-banco-db-1  Healthy                                                                                0.6s
 - Container mexi-banco-app-3 Starting                                                                               0.9s
 ✔ Container mexi-banco-app-2 Created                                                                                0.0s
Error response from daemon: failed to set up container networking: driver failed programming external connectivity on endpoint mexi-banco-app-3 (369e0ea13da881a2e622c5244b12b2a13b56c4b10bdd3615464133dad2ec31b4): Bind for 0.0.0.0:8080 failed: port is already allocated

What's next:
    Debug this Compose error with Gordon → docker ai "help me fix this compose error"
```

### Cambio de puertos a `"8080-8082:8080"` y verificación de nuevo

> Cada réplica quedó en un puerto distinto. La que atiende el **8080** es `app-3`, no `app-1`.

```text
PS C:\Users\PC\Desktop\MexiBanco\mexi-banco> docker compose up -d --scale app=3
[+] up 4/4
 ✔ Container mexi-banco-db-1  Healthy                                                                                0.9s
 ✔ Container mexi-banco-app-1 Started                                                                                1.2s
 ✔ Container mexi-banco-app-2 Started                                                                                1.4s
 ✔ Container mexi-banco-app-3 Started
```

```text
PS C:\Users\PC\Desktop\MexiBanco\mexi-banco> docker compose ps
NAME               IMAGE                COMMAND                  SERVICE   CREATED          STATUS                    PORTS
mexi-banco-app-1   mexi-banco-app       "java -jar app.jar"      app       2 minutes ago    Up 2 minutes (healthy)    0.0.0.0:8081->8080/tcp, [::]:8081->8080/tcp
mexi-banco-app-2   mexi-banco-app       "java -jar app.jar"      app       2 minutes ago    Up 2 minutes (healthy)    0.0.0.0:8082->8080/tcp, [::]:8082->8080/tcp
mexi-banco-app-3   mexi-banco-app       "java -jar app.jar"      app       2 minutes ago    Up 2 minutes (healthy)    0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
mexi-banco-db-1    postgres:17-alpine   "docker-entrypoint.s…"   db        54 minutes ago   Up 54 minutes (healthy)   5432/tcp
```

### Anotaciones del reto

Al escalar a tres réplicas en los puertos 8080, 8081 y 8082 noté que las 10 peticiones que mandé al 8080 siempre las atendió la misma copia, y cuando apagué esa réplica el 8080 dejó de responder aunque las otras dos seguían sanas. Con esto confirmé que Compose no trae un balanceador, porque cada puerto apunta a una sola copia y nadie reparte ni redirige el tráfico entre ellas.



