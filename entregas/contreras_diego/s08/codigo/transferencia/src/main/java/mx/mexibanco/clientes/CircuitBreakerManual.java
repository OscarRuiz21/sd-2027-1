package mx.mexibanco.clientes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Circuit breaker con tres estados:
 * CERRADO: deja pasar todo y cuenta fallas seguidas. Al llegar al umbral, se ABRE.
 * ABIERTO: no llama al servicio; falla de inmediato. Pasado el tiempo de espera, pasa a SEMIABIERTO.
 * SEMIABIERTO: deja pasar llamadas de prueba. Si una falla, vuelve a ABIERTO; si salen
 * bien las pruebas necesarias, se CIERRA.
 */
public class CircuitBreakerManual {

        public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }

        private static final Logger log = LoggerFactory.getLogger(CircuitBreakerManual.class);

        private final String nombre;
        private final int umbralFallas;
        private final Duration esperaAbierto;
        private final int pruebasParaCerrar;
        private final Predicate<RuntimeException> cuentaComoFalla;

        private Estado estado = Estado.CERRADO;
        private int fallasConsecutivas;
        private int exitosSemiabierto;
        private int pruebasEnCurso;
        private Instant abiertoDesde;

        public CircuitBreakerManual(String nombre, int umbralFallas, Duration esperaAbierto,
                                    int pruebasParaCerrar, Predicate<RuntimeException> cuentaComoFalla) {
                this.nombre = nombre;
                this.umbralFallas = umbralFallas;
                this.esperaAbierto = esperaAbierto;
                this.pruebasParaCerrar = pruebasParaCerrar;
                this.cuentaComoFalla = cuentaComoFalla;
        }

        public <T> T ejecutar(Supplier<T> operacion) {
                autorizar();
                T resultado;
                try {
                        resultado = operacion.get();
                } catch (RuntimeException e) {
                        if (cuentaComoFalla.test(e)) {
                                registrarFalla(e);
                        } else {
                                registrarExito(); // el servicio respondio (404, 422...): esta vivo
                        }
                        throw e;
                }
                registrarExito();
                return resultado;
        }

        private synchronized void autorizar() {
                if (estado == Estado.ABIERTO) {
                        if (Duration.between(abiertoDesde, Instant.now()).compareTo(esperaAbierto) >= 0) {
                                exitosSemiabierto = 0;
                                pruebasEnCurso = 0;
                                cambiar(Estado.SEMIABIERTO);
                        } else {
                                throw new CircuitoAbiertoException(nombre);
                        }
                }
                if (estado == Estado.SEMIABIERTO) {
                        if (pruebasEnCurso >= pruebasParaCerrar) {
                                throw new CircuitoAbiertoException(nombre);
                        }
                        pruebasEnCurso++;
                }
        }

        private synchronized void registrarExito() {
                if (estado == Estado.SEMIABIERTO) {
                        pruebasEnCurso = Math.max(0, pruebasEnCurso - 1);
                        exitosSemiabierto++;
                        if (exitosSemiabierto >= pruebasParaCerrar) {
                                fallasConsecutivas = 0;
                                cambiar(Estado.CERRADO);
                        }
                } else if (estado == Estado.CERRADO) {
                        fallasConsecutivas = 0;
                }
        }

        private synchronized void registrarFalla(RuntimeException causa) {
                log.warn("Circuit breaker '{}': llamada fallida ({})", nombre, causa.getMessage());
                if (estado == Estado.SEMIABIERTO) {
                        abrir();
                } else if (estado == Estado.CERRADO) {
                        fallasConsecutivas++;
                        if (fallasConsecutivas >= umbralFallas) {
                                abrir();
                        }
                }
        }

        private void abrir() {
                abiertoDesde = Instant.now();
                pruebasEnCurso = 0;
                cambiar(Estado.ABIERTO);
        }

        private void cambiar(Estado nuevo) {
                log.warn("Circuit breaker '{}': {} -> {}", nombre, estado, nuevo);
                estado = nuevo;
        }

        public synchronized Map<String, Object> resumen() {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("nombre", nombre);
                m.put("estado", estado);
                m.put("fallasConsecutivas", fallasConsecutivas);
                m.put("umbralFallas", umbralFallas);
                m.put("exitosSemiabierto", exitosSemiabierto);
                m.put("pruebasParaCerrar", pruebasParaCerrar);
                m.put("esperaAbiertoSegundos", esperaAbierto.toSeconds());
                return m;
        }
}
