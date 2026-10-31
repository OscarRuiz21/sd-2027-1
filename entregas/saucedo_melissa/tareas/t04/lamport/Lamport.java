import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulacion de relojes de Lamport: 3 servidores (A, B y C) que se mandan 5 mensajes.
 *
 * Reglas (las de Lamport):
 *   - evento local ........ reloj = reloj + 1
 *   - enviar un mensaje ... reloj = reloj + 1, y el mensaje lleva ese valor
 *   - recibir un mensaje .. reloj = max(reloj local, reloj del mensaje) + 1
 *
 * Es una simulacion en un solo hilo y en un orden fijo, para que la salida sea siempre la misma
 * y se pueda explicar paso a paso.
 */
public class Lamport {

    /** Lo que viaja por la red: el texto, quien lo manda y el reloj del emisor al enviarlo. */
    record Mensaje(String texto, String de, long reloj) {
    }

    /** Una linea de la bitacora global. */
    record Evento(String servidor, long reloj, String descripcion) {
    }

    static final List<Evento> bitacora = new ArrayList<>();
    static int mensajes = 0;
    static int respetanCausalidad = 0;

    static class Servidor {
        final String nombre;
        final AtomicLong reloj = new AtomicLong(0);

        Servidor(String nombre) {
            this.nombre = nombre;
        }

        /** Evento local: +1. */
        void evento(String descripcion) {
            registrar(reloj.incrementAndGet(), descripcion);
        }

        /** Envio: +1, y el mensaje viaja con el valor nuevo. */
        Mensaje enviar(String destino, String texto) {
            long t = reloj.incrementAndGet();
            registrar(t, "envia '" + texto + "' a " + destino);
            return new Mensaje(texto, nombre, t);
        }

        /** Recepcion: max(local, recibido) + 1. */
        void recibir(Mensaje m) {
            long t = reloj.updateAndGet(local -> Math.max(local, m.reloj()) + 1);
            registrar(t, "recibe '" + m.texto() + "' de " + m.de() + " (traia " + m.reloj() + ")");
        }

        private void registrar(long t, String descripcion) {
            bitacora.add(new Evento(nombre, t, descripcion));
            System.out.printf("[%s | reloj=%2d] %s%n", nombre, t, descripcion);
        }
    }

    /** Un mensaje completo: el emisor envia, el receptor recibe, y se comprueba la causalidad. */
    static void mensaje(Servidor de, Servidor a, String texto) {
        Mensaje m = de.enviar(a.nombre, texto);
        a.recibir(m);
        mensajes++;
        long envio = m.reloj();
        long recepcion = a.reloj.get();
        if (envio < recepcion) {
            respetanCausalidad++;
        }
    }

    public static void main(String[] args) {
        Servidor a = new Servidor("A");
        Servidor b = new Servidor("B");
        Servidor c = new Servidor("C");

        System.out.println("=== Eventos, en el orden en que ocurren en la simulacion ===");
        a.evento("arranca");
        mensaje(a, b, "m1: pedido");
        b.evento("guarda el pedido");
        c.evento("lee su disco");           // no se habla con nadie: es concurrente con lo de A y B
        mensaje(b, c, "m2: confirmacion");
        mensaje(c, a, "m3: aviso");
        mensaje(a, b, "m4: cierre");
        mensaje(b, a, "m5: recibo");

        System.out.println();
        System.out.println("=== Los mismos eventos, ordenados por reloj de Lamport (empate: por nombre) ===");
        bitacora.stream()
                .sorted(Comparator.comparingLong(Evento::reloj).thenComparing(Evento::servidor))
                .forEach(e -> System.out.printf("reloj=%2d  %s  %s%n", e.reloj(), e.servidor(), e.descripcion()));

        System.out.println();
        System.out.printf("Mensajes: %d. Con reloj de envio < reloj de recepcion: %d. %s%n",
                mensajes, respetanCausalidad, mensajes == respetanCausalidad ? "Causalidad OK" : "ERROR");
        System.out.printf("Relojes finales: A=%d, B=%d, C=%d%n", a.reloj.get(), b.reloj.get(), c.reloj.get());
    }
}
