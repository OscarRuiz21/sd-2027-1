package mx.mexibanco.transferencia;

import org.springframework.stereotype.Component;
import java.util.function.Supplier;

@Component
public class CircuitBreakerManual {
    public enum Estado { CERRADO, ABIERTO, SEMI_ABIERTO }

    private Estado estado = Estado.CERRADO;
    private int fallas = 0;
    private final int UMBRAL_FALLAS = 3; // Al tercer fallo, abrimos el circuito
    private final long TIEMPO_ESPERA_MS = 15000; // 15 segundos de enfriamiento
    private long tiempoApertura = 0;

    public synchronized <T> T ejecutar(Supplier<T> accion, String operacion) {
        long ahora = System.currentTimeMillis();

        // 1. Evaluar si estamos abiertos y si ya pasó el tiempo de castigo
        if (estado == Estado.ABIERTO) {
            if (ahora - tiempoApertura > TIEMPO_ESPERA_MS) {
                System.out.println("⚙️ Circuit Breaker: Tiempo cumplido. Pasando a SEMI_ABIERTO para probar [" + operacion + "]");
                estado = Estado.SEMI_ABIERTO;
            } else {
                // Fallo rápido (Fast Failure): Ni siquiera intentamos tocar la red
                throw new RuntimeException("⚡ Circuit Breaker ABIERTO: Fallo rápido en [" + operacion + "]. El servicio cuenta está caído.");
            }
        }

        // 2. Intentar ejecutar la acción
        try {
            T resultado = accion.get();
            
            // Si tuvo éxito y estábamos en prueba, sanamos el circuito
            if (estado == Estado.SEMI_ABIERTO) {
                System.out.println("✅ Circuit Breaker: Prueba exitosa. Circuito CERRADO.");
                estado = Estado.CERRADO;
                fallas = 0;
            }
            return resultado;
            
        } catch (Exception e) {
            // 3. Si falla la red, sumamos el error
            fallas++;
            System.err.println("❌ Circuit Breaker: Fallo en [" + operacion + "]. Contador: " + fallas);
            
            if (estado == Estado.SEMI_ABIERTO || fallas >= UMBRAL_FALLAS) {
                estado = Estado.ABIERTO;
                tiempoApertura = System.currentTimeMillis();
                System.err.println("🚨 Circuit Breaker: Circuito ABIERTO. Las próximas llamadas fallarán rápido.");
            }
            throw e; 
        }
    }
}