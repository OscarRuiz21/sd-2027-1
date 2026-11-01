import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulación de 3 servidores comunicándose con Relojes Lógicos de Lamport.
 * Tarea 04 - Sistemas Distribuidos
 */
public class LamportSimulator {

    // Registro de un mensaje en la red
    public record Message(int id, String sender, String receiver, String content, long timestamp) {}

    // Registro de eventos para auditoría y verificación de causalidad
    public enum EventType { LOCAL, SEND, RECEIVE }

    public record SystemEvent(
            long sequenceNumber,
            String server,
            EventType type,
            long lamportTime,
            String description,
            Long relatedMessageId,
            Long remoteLamportTime
    ) {}

    private static final AtomicLong eventSequence = new AtomicLong(0);
    private static final List<SystemEvent> eventLog = Collections.synchronizedList(new ArrayList<>());

    private static synchronized void logEvent(SystemEvent event) {
        eventLog.add(event);
        String details = switch (event.type()) {
            case LOCAL -> String.format("[L=%2d] [%-8s] EVENTO LOCAL : %s",
                    event.lamportTime(), event.server(), event.description());
            case SEND -> String.format("[L=%2d] [%-8s] ENVÍO        : %s",
                    event.lamportTime(), event.server(), event.description());
            case RECEIVE -> String.format("[L=%2d] [%-8s] RECEPCIÓN    : %s (Msg L=%d)",
                    event.lamportTime(), event.server(), event.description(), event.remoteLamportTime());
        };
        System.out.println(details);
    }

    public static class Server implements Runnable {
        private final String name;
        private final AtomicLong clock = new AtomicLong(0);
        private final BlockingQueue<Message> inbox;
        private final Map<String, BlockingQueue<Message>> network;
        private final List<String> peers;
        private final int messagesToSend;
        private final AtomicInteger globalSentCounter;
        private final AtomicInteger globalReceivedCounter;
        private final int totalMessagesAcrossSystem;
        private final Random random = new Random();

        public Server(
                String name,
                Map<String, BlockingQueue<Message>> network,
                List<String> peers,
                int messagesToSend,
                AtomicInteger globalSentCounter,
                AtomicInteger globalReceivedCounter,
                int totalMessagesAcrossSystem
        ) {
            this.name = name;
            this.network = network;
            this.inbox = network.get(name);
            this.peers = peers;
            this.messagesToSend = messagesToSend;
            this.globalSentCounter = globalSentCounter;
            this.globalReceivedCounter = globalReceivedCounter;
            this.totalMessagesAcrossSystem = totalMessagesAcrossSystem;
        }

        // Regla 1 de Lamport: +1 en evento local
        public void localEvent(String description) {
            long time = clock.incrementAndGet();
            logEvent(new SystemEvent(
                    eventSequence.incrementAndGet(),
                    name,
                    EventType.LOCAL,
                    time,
                    description,
                    null,
                    null
            ));
        }

        // Regla 1 de Lamport: +1 antes de enviar mensaje
        public void sendMessage(String targetServer, String content, int msgId) {
            long time = clock.incrementAndGet();
            Message msg = new Message(msgId, name, targetServer, content, time);
            logEvent(new SystemEvent(
                    eventSequence.incrementAndGet(),
                    name,
                    EventType.SEND,
                    time,
                    String.format("Mensaje #%d -> %s: \"%s\"", msgId, targetServer, content),
                    (long) msgId,
                    null
            ));
            globalSentCounter.incrementAndGet();
            try {
                // Simular latencia de red variable antes de que llegue al inbox destino
                Thread.sleep(20 + random.nextInt(40));
                network.get(targetServer).put(msg);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Regla 2 de Lamport: al recibir, clock = max(local, recibido) + 1
        public void receiveMessage(Message msg) {
            long time = clock.updateAndGet(current -> Math.max(current, msg.timestamp()) + 1);
            logEvent(new SystemEvent(
                    eventSequence.incrementAndGet(),
                    name,
                    EventType.RECEIVE,
                    time,
                    String.format("Mensaje #%d <- %s: \"%s\"", msg.id(), msg.sender(), msg.content()),
                    (long) msg.id(),
                    msg.timestamp()
            ));
            globalReceivedCounter.incrementAndGet();
        }

        @Override
        public void run() {
            int sent = 0;
            int localCounter = 0;

            // Cada servidor envía entre 3 y 5 mensajes intercalados con eventos locales y recepciones
            while (sent < messagesToSend || globalReceivedCounter.get() < totalMessagesAcrossSystem) {
                // 1. Revisar si hay mensajes pendientes en la bandeja de entrada
                Message incoming = inbox.poll();
                if (incoming != null) {
                    receiveMessage(incoming);
                    try {
                        Thread.sleep(10 + random.nextInt(20));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                // 2. Si aún tiene mensajes por enviar, decide si enviar o hacer evento local
                if (sent < messagesToSend) {
                    boolean doSend = (sent == 0) || random.nextBoolean() || localCounter >= 2;
                    if (doSend) {
                        sent++;
                        String peer = peers.get(random.nextInt(peers.size()));
                        int msgId = (Math.abs(name.hashCode()) % 1000) * 10 + sent;
                        sendMessage(peer, "Actualización " + sent, msgId);
                    } else {
                        localCounter++;
                        localEvent("Cómputo interno #" + localCounter);
                    }
                }

                try {
                    // 3. Pequeña espera por nuevos mensajes
                    Message msg = inbox.poll(40, TimeUnit.MILLISECONDS);
                    if (msg != null) {
                        receiveMessage(msg);
                    }
                    Thread.sleep(15 + random.nextInt(25));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("======================================================================");
        System.out.println("   SIMULACIÓN DE RELOJES DE LAMPORT (3 SERVIDORES, 4 MENSAJES C/U)   ");
        System.out.println("======================================================================\n");

        List<String> serverNames = List.of("Server-A", "Server-B", "Server-C");
        Map<String, BlockingQueue<Message>> network = new ConcurrentHashMap<>();
        for (String s : serverNames) {
            network.put(s, new LinkedBlockingQueue<>());
        }

        // Cada servidor enviará 4 mensajes (entre 3 y 5 según el requerimiento)
        int msgsPerServer = 4;
        int totalExpected = serverNames.size() * msgsPerServer;

        AtomicInteger globalSentCounter = new AtomicInteger(0);
        AtomicInteger globalReceivedCounter = new AtomicInteger(0);

        List<Server> servers = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();

        for (String name : serverNames) {
            List<String> peers = serverNames.stream().filter(s -> !s.equals(name)).toList();
            Server server = new Server(
                    name,
                    network,
                    peers,
                    msgsPerServer,
                    globalSentCounter,
                    globalReceivedCounter,
                    totalExpected
            );
            servers.add(server);
            threads.add(new Thread(server, name));
        }

        // Iniciar servidores concurrentes
        for (Thread t : threads) {
            t.start();
        }

        // Esperar a que terminen
        for (Thread t : threads) {
            t.join();
        }

        System.out.println("\n======================================================================");
        System.out.println("                      VERIFICACIÓN DE CAUSALIDAD                      ");
        System.out.println("======================================================================");

        boolean allValid = true;

        // 1. Verificación de causalidad entre envíos y recepciones: L(send) < L(receive)
        System.out.println("\n1. Verificación de Envíos vs Recepciones (Condición de Reloj de Lamport):");
        Map<Long, SystemEvent> sends = new HashMap<>();
        Map<Long, SystemEvent> receives = new HashMap<>();

        for (SystemEvent e : eventLog) {
            if (e.type() == EventType.SEND) {
                sends.put(e.relatedMessageId(), e);
            } else if (e.type() == EventType.RECEIVE) {
                receives.put(e.relatedMessageId(), e);
            }
        }

        for (Long msgId : sends.keySet()) {
            SystemEvent sendEv = sends.get(msgId);
            SystemEvent recvEv = receives.get(msgId);
            if (recvEv != null) {
                boolean valid = sendEv.lamportTime() < recvEv.lamportTime();
                if (!valid) allValid = false;
                System.out.printf("   Mensaje #%d: [%s Envío L=%2d] -> [%s Recepción L=%2d] | ¿L_envío < L_recepción? %s\n",
                        msgId, sendEv.server(), sendEv.lamportTime(),
                        recvEv.server(), recvEv.lamportTime(), valid ? "SÍ (CORRECTO)" : "NO (ERROR)");
            } else {
                System.out.printf("   Mensaje #%d: No fue recibido.\n", msgId);
                allValid = false;
            }
        }

        // 2. Verificación de orden dentro de cada proceso: e_i < e_{i+1}
        System.out.println("\n2. Verificación de Monotonía Local por Servidor:");
        for (String serverName : serverNames) {
            long prevTime = -1;
            boolean localValid = true;
            for (SystemEvent e : eventLog) {
                if (e.server().equals(serverName)) {
                    if (e.lamportTime() <= prevTime) {
                        localValid = false;
                        allValid = false;
                    }
                    prevTime = e.lamportTime();
                }
            }
            System.out.printf("   Servidor %-8s : Monotonía interna estrictamente creciente -> %s\n",
                    serverName, localValid ? "SÍ (CORRECTO)" : "NO (ERROR)");
        }

        System.out.println("\nResultado final: " + (allValid
                ? "TODAS LAS CONDICIONES DE CAUSALIDAD DE LAMPORT SE CUMPLEN SATISFACTORIAMENTE."
                : "SE DETECTARON VIOLACIONES A LA CONDICIÓN DE LAMPORT."));
        System.out.println("======================================================================\n");
    }
}
