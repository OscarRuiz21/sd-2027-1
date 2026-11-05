import java.util.concurrent.atomic.AtomicLong;

public class LamportDemo {
    static class Mensaje {
        final String id, origen, destino;
        final long tiempo;

        Mensaje(String id, String origen, String destino, long tiempo) {
            this.id = id;
            this.origen = origen;
            this.destino = destino;
            this.tiempo = tiempo;
        }
    }

    static class Servidor {
        final String nombre;
        final AtomicLong reloj = new AtomicLong(0);

        Servidor(String nombre) {
            this.nombre = nombre;
        }

        void local(String descripcion) {
            long tiempo = reloj.incrementAndGet();
            System.out.printf("%s | reloj=%d | LOCAL: %s%n",
                    nombre, tiempo, descripcion);
        }

        Mensaje enviar(Servidor destino, String id) {
            long tiempo = reloj.incrementAndGet();
            System.out.printf("%s | reloj=%d | ENVIA %s a %s%n",
                    nombre, tiempo, id, destino.nombre);
            return new Mensaje(id, nombre, destino.nombre, tiempo);
        }

        void recibir(Mensaje mensaje) {
            if (!nombre.equals(mensaje.destino)) {
                throw new IllegalArgumentException("Destinatario incorrecto");
            }

            long tiempo = reloj.updateAndGet(
                    actual -> Math.max(actual, mensaje.tiempo) + 1);

            System.out.printf("%s | reloj=%d | RECIBE %s de %s (marca=%d)%n",
                    nombre, tiempo, mensaje.id, mensaje.origen, mensaje.tiempo);
        }
    }

    public static void main(String[] args) {
        Servidor s1 = new Servidor("S1");
        Servidor s2 = new Servidor("S2");
        Servidor s3 = new Servidor("S3");

        s1.local("Prepara una solicitud");
        s2.local("Carga su configuracion");
        s3.local("Revisa su estado");

        // M1 queda pendiente para simular un retraso de red.
        Mensaje m1 = s1.enviar(s2, "M1");
        Mensaje m2 = s1.enviar(s3, "M2");
        s3.recibir(m2);

        Mensaje m3 = s3.enviar(s2, "M3");
        s2.recibir(m3);
        s2.recibir(m1);

        Mensaje m4 = s2.enviar(s1, "M4");
        s1.recibir(m4);
        s1.local("Procesa la respuesta");

        Mensaje m5 = s1.enviar(s3, "M5");
        s3.recibir(m5);
    }
}
