import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class LamportDemo {
    record Mensaje(int id, String origen, String destino, long reloj) {}
    static final List<String> auditoria = new ArrayList<>();
    static class Servidor {
        final String nombre;
        final AtomicLong reloj = new AtomicLong();
        Servidor(String nombre) { this.nombre = nombre; }
        long local(String detalle) {
            long antes = reloj.get();
            long ahora = reloj.incrementAndGet();
            assert ahora > antes;
            System.out.printf("%s L=%d %s%n", nombre, ahora, detalle);
            return ahora;
        }
        Mensaje enviar(int id, Servidor destino) {
            return new Mensaje(id, nombre, destino.nombre, local("envia m" + id + " a " + destino.nombre));
        }
        void recibir(Mensaje m) {
            if (!m.destino().equals(nombre)) throw new IllegalArgumentException("Destinatario incorrecto");
            long anterior = reloj.get();
            long ahora = reloj.updateAndGet(v -> Math.max(v, m.reloj()) + 1);
            assert ahora > anterior;
            assert ahora > m.reloj();
            System.out.printf("%s L=%d recibe m%d de %s (L enviado=%d)%n", nombre, ahora, m.id(), m.origen(), m.reloj());
            auditoria.add("m" + m.id() + ": " + m.reloj() + " < " + ahora);
        }
    }
    public static void main(String[] args) {
        Servidor a = new Servidor("A"), b = new Servidor("B"), c = new Servidor("C");
        a.local("evento local");
        c.local("evento independiente");
        Mensaje m1 = a.enviar(1, b);
        Mensaje m2 = a.enviar(2, c);
        c.recibir(m2); // llega antes que m1, sin invertir ninguna dependencia causal
        b.recibir(m1);
        Mensaje m3 = b.enviar(3, c);
        c.recibir(m3);
        Mensaje m4 = c.enviar(4, a);
        a.recibir(m4);
        if (auditoria.size() != 4) throw new AssertionError("Faltan mensajes");
        System.out.println("Verificacion de envio < recepcion: " + String.join("; ", auditoria));
        System.out.println("Cuatro mensajes verificados. Marcas menores no prueban causalidad: A y C empiezan con L=1 sin comunicarse.");
    }
}
