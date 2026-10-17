package mx.mexibanco.transferencia;

import java.time.LocalDateTime;

public class MiCircuitBreaker {
    private enum Estado { CERRADO, ABIERTO, SEMIABIERTO }
    private Estado estado = Estado.CERRADO;
    private int fallos = 0;
    private final int UMBRAL_FALLOS = 3;
    private final int TIEMPO_ESPERA_SEGUNDOS = 10;
    private LocalDateTime tiempoApertura;

    public synchronized void registrarExito() {
        fallos = 0;
        estado = Estado.CERRADO;
    }

    public synchronized void registrarFallo() {
        fallos++;
        if (fallos >= UMBRAL_FALLOS) {
            estado = Estado.ABIERTO;
            tiempoApertura = LocalDateTime.now();
            System.out.println("⚠️ CIRCUIT BREAKER ABIERTO: cuenta no disponible");
        }
    }

    public synchronized boolean permitePaso() {
        if (estado == Estado.CERRADO) return true;
        
        if (estado == Estado.ABIERTO) {
            if (LocalDateTime.now().isAfter(tiempoApertura.plusSeconds(TIEMPO_ESPERA_SEGUNDOS))) {
                estado = Estado.SEMIABIERTO;
                System.out.println("⚠️ CIRCUIT BREAKER SEMIABIERTO: probando si cuenta volvió");
                return true;
            }
            return false; 
        }
        return true; 
    }
}
