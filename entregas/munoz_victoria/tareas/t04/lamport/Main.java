
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    // Representa un mensaje enviado entre servidores
    static class Message {
        String sender;
        String receiver;
        String content;
        long timestamp;

        Message(String sender, String receiver, String content, long timestamp) {
            this.sender = sender;
            this.receiver = receiver;
            this.content = content;
            this.timestamp = timestamp;
        }
    }

    // Representa un servidor con su propio reloj de Lamport
    static class Server {
        String name;
        AtomicLong clock = new AtomicLong(0);

        Server(String name) {
            this.name = name;
        }

        // Evento local: incrementar el reloj
        void localEvent(String description) {
            long time = clock.incrementAndGet();

            System.out.println(
                name + " | EVENTO LOCAL | " +
                description + " | Reloj: " + time
            );
        }

        // Enviar mensaje: incrementar el reloj y adjuntarlo
        Message send(Server destination, String content) {
            long time = clock.incrementAndGet();

            Message message = new Message(
                name,
                destination.name,
                content,
                time
            );

            System.out.println(
                name + " | ENVÍA a " + destination.name +
                " | " + content +
                " | Reloj: " + time
            );

            return message;
        }

        // Recibir mensaje: max(reloj local, recibido) + 1
        void receive(Message message) {
            long time = clock.updateAndGet(
                current -> Math.max(current, message.timestamp) + 1
            );

            System.out.println(
                name + " | RECIBE de " + message.sender +
                " | " + message.content +
                " | Reloj recibido: " + message.timestamp +
                " | Reloj actualizado: " + time
            );
        }
    }

    public static void main(String[] args) {

        // Crear los tres servidores
        Server A = new Server("Servidor A");
        Server B = new Server("Servidor B");
        Server C = new Server("Servidor C");

        System.out.println("=== RELOJES DE LAMPORT ===\n");

        // Evento local inicial
        A.localEvent("Inicia el proceso");

        // Mensaje 1: A -> B
        Message m1 = A.send(B, "Solicitud de información");
        B.receive(m1);

        // Mensaje 2: B -> C
        Message m2 = B.send(C, "Reenvía la solicitud");
        C.receive(m2);

        // Mensaje 3: C -> A
        Message m3 = C.send(A, "Respuesta del servidor C");
        A.receive(m3);

        // Mensaje 4: A -> C
        Message m4 = A.send(C, "Confirmación de respuesta");
        C.receive(m4);

        System.out.println("\n=== ESTADO FINAL DE LOS RELOJES ===");
        System.out.println("Servidor A: " + A.clock.get());
        System.out.println("Servidor B: " + B.clock.get());
        System.out.println("Servidor C: " + C.clock.get());
    }
}