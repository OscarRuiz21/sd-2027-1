import java.util.concurrent.atomic.AtomicLong;

public class LamportSimulacion {

    // Cada mensaje transporta la marca de Lamport del momento del envio.
    static class Mensaje {
        final String id;
        final String origen;
        final String destino;
        final long relojEnviado;

        Mensaje(String id, String origen, String destino, long relojEnviado) {
            this.id = id;
            this.origen = origen;
            this.destino = destino;
            this.relojEnviado = relojEnviado;
        }
    }

    static class Servidor {
        final String nombre;
        final AtomicLong reloj = new AtomicLong(0);

        Servidor(String nombre) {
            this.nombre = nombre;
        }

        void eventoLocal(String descripcion) {
            long actual = reloj.incrementAndGet();
            mostrar(actual, "LOCAL  " + descripcion);
        }

        Mensaje enviar(String id, String destino) {
            long actual = reloj.incrementAndGet();
            mostrar(actual, "ENVIA  " + id + " a " + destino);
            return new Mensaje(id, nombre, destino, actual);
        }

        void recibir(Mensaje mensaje) {
            if (!nombre.equals(mensaje.destino)) {
                throw new IllegalArgumentException("Destinatario incorrecto");
            }
            long actual = reloj.updateAndGet(
                local -> Math.max(local, mensaje.relojEnviado) + 1
            );
            mostrar(actual, "RECIBE " + mensaje.id + " de " + mensaje.origen
                + " (marca enviada=" + mensaje.relojEnviado + ")");
        }

        void mostrar(long actual, String accion) {
            System.out.printf("[%s] L=%-2d | %s%n", nombre, actual, accion);
        }
    }

    public static void main(String[] args) {
        Servidor a = new Servidor("A");
        Servidor b = new Servidor("B");
        Servidor c = new Servidor("C");

        System.out.println("Simulacion de Lamport: 3 servidores y 4 mensajes");

        a.eventoLocal("Prepara un pedido");
        Mensaje m1 = a.enviar("M1", "B");
        Mensaje m2 = a.enviar("M2", "C");

        c.recibir(m2);
        c.eventoLocal("Procesa el cambio recibido");
        Mensaje m3 = c.enviar("M3", "B");

        // B recibe M3 antes que M1 para simular un mensaje retrasado.
        b.recibir(m3);
        b.recibir(m1);
        b.eventoLocal("Revisa los mensajes");
        Mensaje m4 = b.enviar("M4", "A");

        a.recibir(m4);
    }
}
