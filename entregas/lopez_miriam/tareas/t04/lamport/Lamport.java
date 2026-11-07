import java.util.concurrent.atomic.AtomicLong;

public class Lamport {
    static class Servidor {
        private final String nombre;
        private final AtomicLong reloj = new AtomicLong(0);

        Servidor(String nombre) { this.nombre = nombre; }

        void evento(String descripcion) {
            long tiempo = reloj.incrementAndGet();
            System.out.printf("%s | reloj=%d | evento local: %s%n", nombre, tiempo, descripcion);
        }

        long enviar(String destino) {
            long tiempo = reloj.incrementAndGet();
            System.out.printf("%s | reloj=%d | envia mensaje a %s%n", nombre, tiempo, destino);
            return tiempo;
        }

        void recibir(String origen, long tiempoRecibido) {
            long tiempo = reloj.updateAndGet(actual -> Math.max(actual, tiempoRecibido) + 1);
            System.out.printf("%s | reloj=%d | recibe mensaje de %s (marca=%d)%n", nombre, tiempo, origen, tiempoRecibido);
        }
    }

    public static void main(String[] args) {
        Servidor a = new Servidor("Servidor A");
        Servidor b = new Servidor("Servidor B");
        Servidor c = new Servidor("Servidor C");

        a.evento("prepara una transferencia");
        long m1 = a.enviar("Servidor B");
        b.recibir("Servidor A", m1);
        b.evento("valida la transferencia");
        long m2 = b.enviar("Servidor C");
        c.recibir("Servidor B", m2);
        long m3 = c.enviar("Servidor A");
        a.recibir("Servidor C", m3);
        long m4 = a.enviar("Servidor B");
        b.recibir("Servidor A", m4);
    }
}
