package lamport;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulación de Relojes de Lamport en Java para 3 servidores distribuidos.
 * Demuestra la relación de orden causal a -> b => L(a) < L(b).
 */
public class LamportSimulation {

    static class Mensaje {
        final String contenido;
        final long timestampLamport;
        final String emisor;

        public Mensaje(String contenido, long timestampLamport, String emisor) {
            this.contenido = contenido;
            this.timestampLamport = timestampLamport;
            this.emisor = emisor;
        }
    }

    static class Servidor {
        private final String nombre;
        private final AtomicLong relojLamport;

        public Servidor(String nombre) {
            this.nombre = nombre;
            this.relojLamport = new AtomicLong(0);
        }

        // Evento interno local
        public void eventoLocal(String descripcion) {
            long t = relojLamport.incrementAndGet();
            System.out.printf("[%s] Evento Local: '%s' | Reloj Lamport = %d%n", nombre, descripcion, t);
        }

        // Envío de mensaje
        public Mensaje enviarMensaje(String contenido, Servidor destino) {
            long t = relojLamport.incrementAndGet();
            System.out.printf("[%s] ENVIANDO mensaje a %s: '%s' | Reloj Lamport = %d%n", nombre, destino.nombre, contenido, t);
            return new Mensaje(contenido, t, this.nombre);
        }

        // Recepción de mensaje: max(local, recibido) + 1
        public void recibirMensaje(Mensaje msg) {
            long t = relojLamport.updateAndGet(actual -> Math.max(actual, msg.timestampLamport) + 1);
            System.out.printf("[%s] RECIBIDO mensaje de %s: '%s' (TS origen: %d) | Reloj Lamport = %d%n",
                    nombre, msg.emisor, msg.contenido, msg.timestampLamport, t);
        }

        public long getReloj() {
            return relojLamport.get();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=================================================");
        System.out.println(" SIMULACIÓN DE RELOJES DE LAMPORT (3 SERVIDORES) ");
        System.out.println("=================================================\n");

        Servidor s1 = new Servidor("Servidor-1");
        Servidor s2 = new Servidor("Servidor-2");
        Servidor s3 = new Servidor("Servidor-3");

        // Secuencia de eventos interconectados (4 mensajes transcurridos)
        s1.eventoLocal("Inicialización de proceso A1");
        s2.eventoLocal("Inicialización de proceso B1");

        // Mensaje 1: S1 -> S2
        Mensaje m1 = s1.enviarMensaje("Payload M1", s2);
        s2.recibirMensaje(m1);

        // Evento local en S3
        s3.eventoLocal("Procesamiento local C1");

        // Mensaje 2: S2 -> S3
        Mensaje m2 = s2.enviarMensaje("Payload M2", s3);
        s3.recibirMensaje(m2);

        // Mensaje 3: S3 -> S1
        Mensaje m3 = s3.enviarMensaje("Payload M3", s1);
        s1.recibirMensaje(m3);

        // Mensaje 4: S1 -> S2
        Mensaje m4 = s1.enviarMensaje("Payload M4", s2);
        s2.recibirMensaje(m4);

        s3.eventoLocal("Cierre de transacción C2");

        System.out.println("\n-------------------------------------------------");
        System.out.println(" ESTADO FINAL DE RELOJES LÓGICOS");
        System.out.println("-------------------------------------------------");
        System.out.printf("Reloj Servidor-1: %d%n", s1.getReloj());
        System.out.printf("Reloj Servidor-2: %d%n", s2.getReloj());
        System.out.printf("Reloj Servidor-3: %d%n", s3.getReloj());
        System.out.println("=================================================");
    }
}