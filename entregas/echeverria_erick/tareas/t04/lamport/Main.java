import java.util.concurrent.atomic.AtomicLong;

class Servidor{
    private final String nombre;
    private final AtomicLong reloj = new AtomicLong(0);

    public Servidor(String nombre) {
        this.nombre = nombre;
    }

    // Evento local: incrementa en 1
    public void eventoLocal(String descripcion) {
        long t = reloj.incrementAndGet();
        System.out.printf("[%s] EVENTO LOCAL: %s | Reloj: %d%n", nombre, descripcion, t);
    }

    // Envío de mensaje: incrementa en 1 y transmite su timestamp
    public void enviarMensaje(Servidor destino, String mensaje) {
        long tEnvio = reloj.incrementAndGet();
        System.out.printf("[%s] ENVÍA a [%s]: \"%s\" | Reloj: %d%n", nombre, destino.nombre, mensaje, tEnvio);
        destino.recibirMensaje(this.nombre, tEnvio, mensaje);
    }

    // Recepción de mensaje: max(local, recibido) + 1
    public void recibirMensaje(String emisor, long timestampRecibido, String mensaje) {
        long tRecibido = reloj.updateAndGet(actual -> Math.max(actual, timestampRecibido) + 1);
        System.out.printf("[%s] RECIBE de [%s]: \"%s\" | Reloj: %d%n", nombre, emisor, mensaje, tRecibido);
    }
}

public class Main {
    public static void main(String[] args) {
        Servidor s1 = new Servidor("Servidor-1");
        Servidor s2 = new Servidor("Servidor-2");
        Servidor s3 = new Servidor("Servidor-3");

        System.out.println("=== SIMULACIÓN DE RELOJES DE LAMPORT ===\n");

        s1.eventoLocal("Procesar registros iniciales");
        s1.enviarMensaje(s2, "M1: Actualización de estado");  // Mensaje 1
        s2.eventoLocal("Actualizar cache");
        s2.enviarMensaje(s3, "M2: Notificación en cascada"); // Mensaje 2
        s3.enviarMensaje(s1, "M3: Confirmación a S1");       // Mensaje 3
        s1.enviarMensaje(s3, "M4: Cierre de hilo de ejecución");// Mensaje 4

        System.out.println("\n=== FIN DE SIMULACIÓN ===");
    }
}