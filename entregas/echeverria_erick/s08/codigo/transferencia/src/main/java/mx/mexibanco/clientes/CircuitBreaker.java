package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Circuito breaker a mano, tres estados:
 * CERRADO: pasan las llamadas y se cuentas las fallas seguidas.
 * ABIERTO: no se llama al servicio: se falla al instante.
 * MEDIO_ABIERTO: pasado el tiempo de espera, deja pasar una llamada de prueba.
 */

public class CircuitBreaker{
    public enum Estado{
        CERRADO, 
        ABIERTO,
        MEDIO_ABIERTO
    }

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

        private final String nombre;
        private final int umbral;
        private final Duration espera;

        private Estado estado = Estado.CERRADO;
        private int fallas;
        private Instant desde;
        private boolean pruebaEnCurso;

        public CircuitBreaker(String nombre, int umbral, Duration espera) {
            this.nombre = nombre;
            this.umbral = umbral;
            this.espera = espera;
        }
        // Ejecuta una llamada al servicio protegido
        // Controla si se permite la llamada y registra su resultado
        public <T> T llamar(Supplier<T> llamada) {
            permitir();
            try {
                // Ejecuta la operacion real, si termina correctamente, registra exito.
                T resultado = llamada.get();
                exito();
                return resultado;
            } catch (ErrorRemotoException e) {
                // Los errores 4xx indican que el servicio respondio,
                // aunque la peticion haya sido rechazada.
                // Por eso, en esta implementación no cuentan como fallas del servicio. 
                if (e.getEstado() < 500) {
                    exito();
                } else {
                    falla();
                }
                throw e;
            } catch (RuntimeException e) {
                // sin conexion, timeout, este se considera como un problema remoto
                // se transforma el error en una excepcion con codigo 503
                falla();
                throw new ErrorRemotoException(nombre, 503, "No se pudo contactar a " + nombre + ": " + e.getMessage());
            }
        }

        public synchronized Estado estado() {
            return estado;
        }

        // Decide si se puede ejecutar una llamada, 
        // si el circuito sigue abierto rechaza la peticion inmediatamente
        private synchronized void permitir() {
            if (estado == Estado.ABIERTO) {
                if (Instant.now().isBefore(desde.plus(espera))) {
                    throw rechazo();
                }
                cambiarA(Estado.MEDIO_ABIERTO);
            }
            if (estado == Estado.MEDIO_ABIERTO) {
                if (pruebaEnCurso) {
                    throw rechazo(); 
                }
                pruebaEnCurso = true;
            }
        }

        // Registra que una llamada termino correctamente
        private synchronized void exito() {
            if (estado == Estado.ABIERTO) {
                return;   
            }
            fallas = 0;
            pruebaEnCurso = false;
            if (estado != Estado.CERRADO) {
                cambiarA(Estado.CERRADO);
            }
        }

        // Registra una falla y determina si debe abrirse el circuito
        private synchronized void falla() {
            if (estado == Estado.ABIERTO) {
                return;
            }
            pruebaEnCurso = false;
            fallas++;
            if (estado == Estado.MEDIO_ABIERTO || fallas >= umbral) {
                desde = Instant.now();
                cambiarA(Estado.ABIERTO);
            }
        }

        // Cambia el estado y registra la transicion en los logs
        private void cambiarA(Estado nuevo) {
            log.info("Circuito de {}: {} -> {}", nombre, estado, nuevo);
            estado = nuevo;
        }

        // Construye la excepcion que se lanza cuando una llamada es rechazada por el circuito
        private ErrorRemotoException rechazo() {
            return new ErrorRemotoException(nombre, 503,
                "Circuito abierto: " + nombre + " no está disponible, se rechaza sin llamarlo");
        }
}