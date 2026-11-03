import java.util.concurrent.atomic.AtomicLong;
import java.util.Random;

class Server implements Runnable {
    private final String serverName;
    private final AtomicLong lamportClock;
    private final Server[] connectedServers;
    private final Random randomNumberGenerator;

    public Server(String serverName, Server[] connectedServers) {
        this.serverName = serverName;
        this.lamportClock = new AtomicLong(0);
        this.connectedServers = connectedServers;
        this.randomNumberGenerator = new Random();
    }

    public String getServerName() {
        return serverName;
    }

    public void setConnectedServers(Server[] connectedServers) {
        System.arraycopy(connectedServers, 0, this.connectedServers, 0, connectedServers.length);
    }

    public void performLocalEvent() {
        long currentTimestamp = lamportClock.incrementAndGet();
        System.out.printf("[%s] Evento local. Reloj: %d%n", serverName, currentTimestamp);
    }

    public void sendMessageTo(Server destinationServer) {
        long currentTimestamp = lamportClock.incrementAndGet();
        System.out.printf("[%s] Enviando mensaje a %s. Reloj (enviado): %d%n", serverName, destinationServer.getServerName(), currentTimestamp);
        destinationServer.receiveMessageFrom(this, currentTimestamp);
    }

    public void receiveMessageFrom(Server senderServer, long receivedTimestamp) {
        long currentTimestamp = lamportClock.get();
        long newTimestamp = Math.max(currentTimestamp, receivedTimestamp) + 1;
        
        lamportClock.set(newTimestamp);
        
        System.out.printf("[%s] Mensaje recibido de %s (timestamp: %d). Reloj actualizado: %d%n", 
            serverName, senderServer.getServerName(), receivedTimestamp, newTimestamp);
    }

    @Override
    public void run() {
        int numberOfEvents = 3 + randomNumberGenerator.nextInt(3); 
        
        for (int eventIndex = 0; eventIndex < numberOfEvents; eventIndex++) {
            try {
                Thread.sleep(randomNumberGenerator.nextInt(500)); 
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }

            int actionType = randomNumberGenerator.nextInt(2);
            
            if (actionType == 0) {
                performLocalEvent();
            } else {
                Server destinationServer = connectedServers[randomNumberGenerator.nextInt(connectedServers.length)];
                
                while (destinationServer == this) {
                   destinationServer = connectedServers[randomNumberGenerator.nextInt(connectedServers.length)];
                }
                
                sendMessageTo(destinationServer);
            }
        }
    }
}

public class LamportSimulation {
    public static void main(String[] args) {
        System.out.println("Simulación de Relojes de Lamport: ");
        
        Server server1 = new Server("Servidor-1", new Server[2]);
        Server server2 = new Server("Servidor-2", new Server[2]);
        Server server3 = new Server("Servidor-3", new Server[2]);
        
        server1.setConnectedServers(new Server[]{server2, server3});
        server2.setConnectedServers(new Server[]{server1, server3});
        server3.setConnectedServers(new Server[]{server1, server2});

        Thread thread1 = new Thread(server1);
        Thread thread2 = new Thread(server2);
        Thread thread3 = new Thread(server3);

        thread1.start();
        thread2.start();
        thread3.start();

        try {
            thread1.join();
            thread2.join();
            thread3.join();
        } catch (InterruptedException exception) {
            exception.printStackTrace();
        }
        
        System.out.println("Simulación terminada");
    }
}