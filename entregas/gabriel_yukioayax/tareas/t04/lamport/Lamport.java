import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reloj de Lamport con 3 servidores (hilos) que se mandan 4 mensajes.
 *
 * Reglas:
 *   evento local -> reloj = reloj + 1
 *   enviar       -> reloj = reloj + 1, y el mensaje lleva ese valor
 *   recibir      -> reloj = max(reloj, valor del mensaje) + 1
 *
 * Compilar y correr (Java 17 o mas nuevo):
 *   javac Lamport.java
 *   java Lamport
 */
public class Lamport {

    /** Lo que viaja por la "red": quien lo manda, su nombre y el reloj del que lo mando. */
    record Mensaje(String id, String de, long reloj) { }

    /** Un evento ya ocurrido, para revisar la causalidad al final. */
    record Evento(String servidor, String tipo, String detalle, long reloj) { }

    static final Map<String, Servidor> servidores = new ConcurrentHashMap<>();
    static final List<Evento> bitacora = new ArrayList<>();

    static synchronized void imprimir(Evento e) {
        bitacora.add(e);
        System.out.printf("%-3s | %-8s | %-30s | reloj = %d%n", e.servidor(), e.tipo(), e.detalle(), e.reloj());
    }

    static class Servidor extends Thread {
        final String nombre;
        final AtomicLong reloj = new AtomicLong(0);
        final LinkedBlockingQueue<Mensaje> buzon = new LinkedBlockingQueue<>();
        private Runnable[] guion;

        Servidor(String nombre) {
            this.nombre = nombre;
        }

        void guion(Runnable... pasos) {
            this.guion = pasos;
        }

        void local(String que) {
            long r = reloj.incrementAndGet();                       // +1
            imprimir(new Evento(nombre, "LOCAL", que, r));
        }

        void enviar(String id, String a) {
            long r = reloj.incrementAndGet();                       // +1 y lo manda en el mensaje
            imprimir(new Evento(nombre, "ENVIO", id + " -> " + a, r));
            servidores.get(a).buzon.add(new Mensaje(id, nombre, r));
        }

        void recibir() {
            try {
                Mensaje m = buzon.take();                           // espera a que llegue
                long antes = reloj.get();
                long r = reloj.updateAndGet(local -> Math.max(local, m.reloj()) + 1);
                imprimir(new Evento(nombre, "RECIBO", m.id() + " <- " + m.de()
                    + " max(" + antes + "," + m.reloj() + ")+1", r));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        static void pausa(long ms) {
            try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        @Override
        public void run() {
            for (Runnable paso : guion) {
                paso.run();
                pausa(150);   // para que la salida se pueda seguir; el orden real lo dan los mensajes
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Servidor s1 = new Servidor("S1"), s2 = new Servidor("S2"), s3 = new Servidor("S3");
        for (Servidor s : List.of(s1, s2, s3)) servidores.put(s.nombre, s);

        // S1 trabaja mucho al principio: su reloj se adelanta.
        s1.guion(
            () -> s1.local("valida la sesion"),
            () -> s1.local("lee la cuenta"),
            () -> s1.local("calcula comision"),
            () -> s1.enviar("m1", "S2"),
            () -> s1.recibir(),                 // m3 de S3
            () -> s1.enviar("m4", "S2"));

        // S2 recibe m1 de un reloj adelantado: tiene que saltar.
        s2.guion(
            () -> s2.local("arranca"),
            () -> s2.recibir(),                 // m1 de S1
            () -> s2.enviar("m2", "S3"),
            () -> s2.recibir());                // m4 de S1

        // S3 hace mas eventos locales que nadie mientras espera: cuando le llega m2 su reloj ya es mayor.
        s3.guion(
            () -> s3.local("limpia cache"),
            () -> s3.local("rota logs"),
            () -> s3.local("revisa salud"),
            () -> s3.local("compacta"),
            () -> s3.local("escribe metricas"),
            () -> s3.local("respalda"),
            () -> s3.recibir(),                 // m2 de S2
            () -> s3.enviar("m3", "S1"));

        System.out.println("srv | evento   | detalle                        | reloj");
        System.out.println("----+----------+--------------------------------+---------");
        for (Servidor s : List.of(s1, s2, s3)) s.start();
        for (Servidor s : List.of(s1, s2, s3)) s.join();

        // Revision: para cada mensaje, el reloj del envio tiene que ser menor que el de la recepcion.
        System.out.println();
        System.out.println("Revision de causalidad (envio -> recepcion):");
        boolean todoBien = true;
        for (Evento envio : bitacora) {
            if (!envio.tipo().equals("ENVIO")) continue;
            String id = envio.detalle().split(" ")[0];
            for (Evento rec : bitacora) {
                if (rec.tipo().equals("RECIBO") && rec.detalle().startsWith(id + " ")) {
                    boolean ok = envio.reloj() < rec.reloj();
                    todoBien &= ok;
                    System.out.printf("  %s: envio %s con reloj %d -> recibio %s con reloj %d  %s%n",
                        id, envio.servidor(), envio.reloj(), rec.servidor(), rec.reloj(), ok ? "OK" : "MAL");
                }
            }
        }
        System.out.println(todoBien ? "Todos los mensajes se recibieron con un reloj mayor al del envio."
                                    : "Hay un mensaje que viola la causalidad.");
        System.out.printf("Relojes finales: S1=%d, S2=%d, S3=%d%n", s1.reloj.get(), s2.reloj.get(), s3.reloj.get());
    }
}
