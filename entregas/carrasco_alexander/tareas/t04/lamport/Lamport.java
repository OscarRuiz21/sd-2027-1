import java.util.concurrent.atomic.AtomicLong;

public class Lamport {

    static class Servidor {
        final String nombre;
        final AtomicLong reloj = new AtomicLong(0);

        Servidor(String nombre) { this.nombre = nombre; }

        void eventoLocal(String desc) {
            long t = reloj.incrementAndGet();
            System.out.printf("[%s] reloj=%d | evento local: %s%n", nombre, t, desc);
        }

        long enviar(Servidor destino, String msg) {
            long t = reloj.incrementAndGet();
            System.out.printf("[%s] reloj=%d | ENVIA '%s' a %s%n", nombre, t, msg, destino.nombre);
            return t;
        }

        void recibir(Servidor origen, String msg, long tRecibido) {
            long t = reloj.updateAndGet(local -> Math.max(local, tRecibido) + 1);
            System.out.printf("[%s] reloj=%d | RECIBE '%s' de %s (traia %d)%n",
                    nombre, t, msg, origen.nombre, tRecibido);
        }

        void mandar(Servidor destino, String msg) {
            long ts = enviar(destino, msg);
            destino.recibir(this, msg, ts);
        }
    }

    public static void main(String[] args) {
        Servidor a = new Servidor("A");
        Servidor b = new Servidor("B");
        Servidor c = new Servidor("C");

        c.eventoLocal("arranca");
        a.eventoLocal("inicia");
        a.mandar(b, "m1");
        b.eventoLocal("procesa m1");
        b.mandar(c, "m2");
        c.mandar(a, "m3");
        a.mandar(b, "m4");
        b.mandar(c, "m5");
    }
}