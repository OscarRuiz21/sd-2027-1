import java.util.concurrent.atomic.AtomicLong;

public class Lamport {
    static class Servidor {
        private final String nombre;
        private final AtomicLong reloj = new AtomicLong(0);

        Servidor(String nombre) {
            this.nombre = nombre;
        }

        long eventoLocal(String descripcion) {
            long t = reloj.incrementAndGet();
            imprimir(descripcion, t);
            return t;
        }

        void enviar(Servidor destino, String mensaje) {
            long tEnvio = reloj.incrementAndGet();
            imprimir("ENVÍA a " + destino.nombre + ": " + mensaje, tEnvio);
            destino.recibir(this.nombre, mensaje, tEnvio);
        }

        void recibir(String origen, String mensaje, long relojRecibido) {
            long t = reloj.updateAndGet(actual -> Math.max(actual, relojRecibido) + 1);
            imprimir("RECIBE de " + origen + ": " + mensaje, t);
        }

        private void imprimir(String evento, long t) {
            System.out.printf("%-8s | reloj=%-2d | %s%n", nombre, t, evento);
        }
    }

    public static void main(String[] args) {
        Servidor A = new Servidor("Servidor A");
        Servidor B = new Servidor("Servidor B");
        Servidor C = new Servidor("Servidor C");

        A.eventoLocal("inicia una solicitud");
        A.enviar(B, "M1: solicitud");
        B.enviar(C, "M2: solicitud recibida y reenviada");
        C.eventoLocal("registra la solicitud");
        C.enviar(A, "M3: confirmación");
        A.enviar(C, "M4: consulta de estado");
        B.enviar(A, "M5: aviso independiente");
    }
}
