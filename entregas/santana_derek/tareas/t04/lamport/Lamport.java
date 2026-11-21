import java.util.concurrent.atomic.AtomicLong;

/** Simulación determinista: 3 servidores lógicos, 4 mensajes, sin reloj de pared. */
public class Lamport {
    record Mensaje(String id, String origen, long reloj) {}
    static final class Servidor {
        final String id;
        final AtomicLong reloj = new AtomicLong();
        Servidor(String id) { this.id = id; }
        void local() { System.out.printf("%s local L=%d%n", id, reloj.incrementAndGet()); }
        Mensaje enviar(String mensaje, String destino) {
            long t = reloj.incrementAndGet();
            System.out.printf("%s envia %s a %s L=%d%n", id, mensaje, destino, t);
            return new Mensaje(mensaje, id, t);
        }
        void recibir(Mensaje m) {
            long anterior = reloj.get();
            long t = reloj.updateAndGet(local -> Math.max(local, m.reloj()) + 1);
            if (t <= anterior || t <= m.reloj()) throw new AssertionError("Causalidad rota");
            System.out.printf("%s recibe %s de %s L=%d (enviado L=%d)%n", id, m.id(), m.origen(), t, m.reloj());
        }
    }
    public static void main(String[] args) {
        var a = new Servidor("A"); var b = new Servidor("B"); var c = new Servidor("C");
        a.local(); c.local();
        var m1 = a.enviar("m1", "B");
        var m2 = c.enviar("m2", "B");
        b.recibir(m1); b.recibir(m2);
        var m3 = b.enviar("m3", "C"); c.recibir(m3);
        var m4 = c.enviar("m4", "A"); a.recibir(m4);
        System.out.println("OK: 4 mensajes; todas las recepciones superan envio y reloj local anterior.");
    }
}
