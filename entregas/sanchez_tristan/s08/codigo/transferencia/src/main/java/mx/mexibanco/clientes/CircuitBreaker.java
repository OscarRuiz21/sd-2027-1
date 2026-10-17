package mx.mexibanco.clientes;

import mx.mexibanco.compartido.CircuitoAbiertoException;
import mx.mexibanco.compartido.ErrorRemotoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Circuit Breaker artesanal (implementado a mano) con los tres estados clasicos:
 * CERRADO, ABIERTO y SEMIABIERTO.
 *
 * Protege las llamadas al servicio cuenta para evitar colgar al cliente o saturar
 * un servicio que esta caido o recuperandose.
 */
@Component
public class CircuitBreaker {

	public enum Estado {
		CERRADO,
		ABIERTO,
		SEMIABIERTO
	}

	private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

	private final String servicio = "cuenta";
	private final int umbralFallas;
	private final long tiempoEsperaMs;

	private final AtomicReference<Estado> estado = new AtomicReference<>(Estado.CERRADO);
	private final AtomicInteger fallasConsecutivas = new AtomicInteger(0);
	private volatile long ultimoCambioEstadoMs = System.currentTimeMillis();

	public CircuitBreaker(
			@Value("${mexibanco.circuit-breaker.umbral-fallas:3}") int umbralFallas,
			@Value("${mexibanco.circuit-breaker.tiempo-espera-ms:10000}") long tiempoEsperaMs) {
		this.umbralFallas = umbralFallas;
		this.tiempoEsperaMs = tiempoEsperaMs;
	}

	/**
	 * Ejecuta una llamada protegida por el Circuit Breaker.
	 */
	public synchronized <T> T ejecutar(Supplier<T> llamada) {
		verificarEstadoTransicion();

		if (estado.get() == Estado.ABIERTO) {
			long transcurrido = System.currentTimeMillis() - ultimoCambioEstadoMs;
			long restantesMs = Math.max(0, tiempoEsperaMs - transcurrido);
			log.warn("CircuitBreaker [{}]: peticion rechazada (ABIERTO). Faltan {} ms para prueba semiabierta.",
					servicio, restantesMs);
			throw new CircuitoAbiertoException(servicio,
					String.format("El circuito para '%s' esta ABIERTO (fail-fast). Faltan %.1f s para reintentar.",
							servicio, restantesMs / 1000.0));
		}

		try {
			T resultado = llamada.get();
			registrarExito();
			return resultado;
		} catch (Exception e) {
			if (esFallaDeInfraestructura(e)) {
				registrarFalla(e);
			}
			throw e;
		}
	}

	private void verificarEstadoTransicion() {
		if (estado.get() == Estado.ABIERTO) {
			long tiempoEnAbierto = System.currentTimeMillis() - ultimoCambioEstadoMs;
			if (tiempoEnAbierto >= tiempoEsperaMs) {
				transicionarA(Estado.SEMIABIERTO, "Tiempo de espera cumplido (" + tiempoEsperaMs + " ms)");
			}
		}
	}

	private void registrarExito() {
		Estado actual = estado.get();
		if (actual == Estado.SEMIABIERTO) {
			fallasConsecutivas.set(0);
			transicionarA(Estado.CERRADO, "Llamada de prueba exitosa en semiabierto");
		} else if (actual == Estado.CERRADO && fallasConsecutivas.get() > 0) {
			fallasConsecutivas.set(0);
		}
	}

	private void registrarFalla(Exception e) {
		Estado actual = estado.get();
		if (actual == Estado.SEMIABIERTO) {
			transicionarA(Estado.ABIERTO, "Fallo llamada de prueba en semiabierto: " + e.getMessage());
		} else if (actual == Estado.CERRADO) {
			int fallas = fallasConsecutivas.incrementAndGet();
			log.warn("CircuitBreaker [{}]: falla {}/{} detectada: {}", servicio, fallas, umbralFallas, e.getMessage());
			if (fallas >= umbralFallas) {
				transicionarA(Estado.ABIERTO, "Se alcanzo el umbral de fallas consecutivas (" + umbralFallas + ")");
			}
		}
	}

	private void transicionarA(Estado nuevoEstado, String motivo) {
		Estado anterior = estado.getAndSet(nuevoEstado);
		ultimoCambioEstadoMs = System.currentTimeMillis();
		log.info("CircuitBreaker [{}]: cambio de estado {} -> {}. Motivo: {}",
				servicio, anterior, nuevoEstado, motivo);
	}

	private boolean esFallaDeInfraestructura(Exception e) {
		if (e instanceof ResourceAccessException) {
			return true; // Timeout, connection refused, host no responde
		}
		if (e instanceof ErrorRemotoException errorRemoto) {
			return errorRemoto.getEstado() >= 500; // 500, 502, 503, 504
		}
		// Cualquier otra excepcion no esperada del sistema
		return !(e instanceof mx.mexibanco.compartido.NoEncontradoException ||
				 e instanceof mx.mexibanco.compartido.ReglaDeNegocioException ||
				 e instanceof mx.mexibanco.compartido.ConflictoException);
	}

	public Map<String, Object> getReporte() {
		verificarEstadoTransicion();
		Map<String, Object> reporte = new LinkedHashMap<>();
		Estado actual = estado.get();
		reporte.put("servicio", servicio);
		reporte.put("estado", actual.name());
		reporte.put("fallasConsecutivas", fallasConsecutivas.get());
		reporte.put("umbralFallas", umbralFallas);
		reporte.put("tiempoEsperaMs", tiempoEsperaMs);

		long transcurrido = System.currentTimeMillis() - ultimoCambioEstadoMs;
		long restanteMs = (actual == Estado.ABIERTO) ? Math.max(0, tiempoEsperaMs - transcurrido) : 0;
		reporte.put("segundosRestantesParaSemiabierto", Math.round(restanteMs / 100.0) / 10.0);
		return reporte;
	}

	public Estado getEstado() {
		verificarEstadoTransicion();
		return estado.get();
	}
}
