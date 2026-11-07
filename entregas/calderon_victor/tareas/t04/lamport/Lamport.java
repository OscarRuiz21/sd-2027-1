import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * T04 (opcional): tres servidores que se mandan 4 mensajes, cada uno con su reloj de Lamport.
 *
 * Reglas (un AtomicLong por servidor):
 *   - evento local: reloj + 1
 *   - envio:        reloj + 1, y el mensaje viaja con ese valor
 *   - recepcion:    max(reloj, recibido) + 1
 *
 * Cada servidor es un hilo con un "guion" de pasos. Los hilos duermen un tiempo al azar
 * entre pasos para que el orden en que se imprimen cambie de una corrida a otra; los
 * valores del reloj no cambian, porque solo dependen de los mensajes que se esperan.
 *
 * Correr con: java Lamport.java
 */
public class Lamport {

    /** Un mensaje lleva quien lo manda, su nombre (m1, m2...) y el reloj del que lo envia. */
    record Mensaje(String id, String de, long reloj) {}

    /** Un evento ya ocurrido, para el resumen del final. */
    record Evento(String servidor, long reloj, String tipo, String detalle) {}

    /** Un paso del guion de cada servidor. */
    sealed interface Paso permits Local, Envia, Recibe {}
    record Local(String que) implements Paso {}
    record Envia(String id, String a) implements Paso {}
    record Recibe(String id, String de) implements Paso {}

    static final List<Evento> EVENTOS = new ArrayList<>();
    static final AtomicInteger ORDEN_DE_IMPRESION = new AtomicInteger();

    /** Buzones: uno por cada pareja (remitente -> destinatario), asi cada Recibe espera al mensaje correcto. */
    static final Map<String, BlockingQueue<Mensaje>> BUZONES = new ConcurrentHashMap<>();

    static BlockingQueue<Mensaje> buzon(String de, String a) {
        return BUZONES.computeIfAbsent(de + "->" + a, k -> new LinkedBlockingQueue<>());
    }

    static class Servidor implements Runnable {
        final String nombre;
        final List<Paso> guion;
        final AtomicLong reloj = new AtomicLong(0);

        Servidor(String nombre, List<Paso> guion) {
            this.nombre = nombre;
            this.guion = guion;
        }

        @Override
        public void run() {
            try {
                for (Paso paso : guion) {
                    Thread.sleep(ThreadLocalRandom.current().nextInt(20, 120));
                    switch (paso) {
                        case Local l -> {
                            long t = reloj.incrementAndGet();
                            anota(t, "LOCAL", l.que());
                        }
                        case Envia e -> {
                            long t = reloj.incrementAndGet();
                            // se anota antes de soltar el mensaje, para que nunca se imprima
                            // una recepcion antes que su envio
                            anota(t, "ENVIA", e.id() + " a " + e.a() + " (lleva L=" + t + ")");
                            buzon(nombre, e.a()).put(new Mensaje(e.id(), nombre, t));
                        }
                        case Recibe r -> {
                            Mensaje m = buzon(r.de(), nombre).take();   // espera hasta que llegue
                            long antes = reloj.get();
                            long t = reloj.updateAndGet(local -> Math.max(local, m.reloj()) + 1);
                            anota(t, "RECIBE", m.id() + " de " + m.de() + " (traia L=" + m.reloj()
                                    + ", yo iba en " + antes + " -> max(" + antes + "," + m.reloj() + ")+1)");
                        }
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        void anota(long t, String tipo, String detalle) {
            synchronized (EVENTOS) {
                EVENTOS.add(new Evento(nombre, t, tipo, detalle));
                System.out.printf("%2d. [%s] L=%-2d %-6s %s%n",
                        ORDEN_DE_IMPRESION.incrementAndGet(), nombre, t, tipo, detalle);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // m1: S1 -> S2, m2: S1 -> S3, m3: S2 -> S3, m4: S3 -> S1
        List<Servidor> servidores = List.of(
                new Servidor("S1", List.of(
                        new Local("valida la transferencia"),
                        new Envia("m1", "S2"),
                        new Local("escribe en su bitacora"),
                        new Envia("m2", "S3"),
                        new Recibe("m4", "S3"))),
                new Servidor("S2", List.of(
                        new Recibe("m1", "S1"),
                        new Local("aplica el cargo"),
                        new Envia("m3", "S3"))),
                new Servidor("S3", List.of(
                        new Local("atiende consulta 1"),
                        new Local("atiende consulta 2"),
                        new Local("atiende consulta 3"),
                        new Local("atiende consulta 4"),
                        new Local("atiende consulta 5"),
                        new Recibe("m2", "S1"),
                        new Recibe("m3", "S2"),
                        new Envia("m4", "S1"))));

        System.out.println("=== Eventos en el orden en que ocurrieron (cambia en cada corrida) ===");
        List<Thread> hilos = new ArrayList<>();
        for (Servidor s : servidores) {
            Thread h = new Thread(s, s.nombre);
            hilos.add(h);
            h.start();
        }
        for (Thread h : hilos) {
            h.join();
        }

        System.out.println();
        System.out.println("=== Orden total de Lamport: por L y, si empatan, por nombre del servidor ===");
        EVENTOS.stream()
                .sorted(Comparator.comparingLong(Evento::reloj).thenComparing(Evento::servidor))
                .forEach(e -> System.out.printf("L=%-2d [%s] %-6s %s%n", e.reloj(), e.servidor(), e.tipo(), e.detalle()));

        System.out.println();
        System.out.println("=== Causalidad: cada mensaje se recibe con un reloj mayor al del envio ===");
        for (Evento envio : EVENTOS) {
            if (!envio.tipo().equals("ENVIA")) continue;
            String id = envio.detalle().substring(0, envio.detalle().indexOf(' '));
            Evento recibe = EVENTOS.stream()
                    .filter(e -> e.tipo().equals("RECIBE") && e.detalle().startsWith(id + " "))
                    .findFirst().orElseThrow();
            System.out.printf("%s: envio %s L=%d  <  recepcion %s L=%d   %s%n", id, envio.servidor(), envio.reloj(),
                    recibe.servidor(), recibe.reloj(), envio.reloj() < recibe.reloj() ? "OK" : "FALLA");
        }

        System.out.println();
        System.out.println("=== Reloj final de cada servidor ===");
        for (Servidor s : servidores) {
            System.out.println(s.nombre + ": L=" + s.reloj.get());
        }
    }
}
