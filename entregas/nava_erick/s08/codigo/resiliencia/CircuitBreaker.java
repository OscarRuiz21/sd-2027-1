package mx.mexibanco.resiliencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.LongSupplier;

/**
 * Circuit breaker hecho a mano, con los tres estados:
 *
 *   CERRADO     las llamadas pasas
 *   ABIERTO      Se rechaza toda llamada al instante, sin intentarla (no golpea al servicio caido ni
 *                deja al cliente colgado).
 *   SEMIABIERTO  Se deja pasar una sola llamada de prueba
 *
 */
public class CircuitBreaker {

	public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }

	/** Se lanza cuando el circuito esta abierto y la llamada se rechaza sin intentarla. */
	public static class CircuitoAbiertoException extends RuntimeException {
		public CircuitoAbiertoException(String mensaje) {
			super(mensaje);
		}
	}

	private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

	private final String nombre;
	private final int umbralDeFallas;
	private final long esperaAbiertoMs;
	private final Predicate<RuntimeException> esFallaDelServicio;
	private final LongSupplier reloj;

	private Estado estado = Estado.CERRADO;
	private int fallasSeguidas = 0;
	private long abiertoDesdeMs = 0;
	private boolean pruebaEnCurso = false;

	public CircuitBreaker(String nombre, int umbralDeFallas, Duration esperaAbierto,
						  Predicate<RuntimeException> esFallaDelServicio) {
		this(nombre, umbralDeFallas, esperaAbierto, esFallaDelServicio, System::currentTimeMillis);
	}

	/**reloj propio. */
	public CircuitBreaker(String nombre, int umbralDeFallas, Duration esperaAbierto,
						  Predicate<RuntimeException> esFallaDelServicio, LongSupplier reloj) {
		this.nombre = nombre;
		this.umbralDeFallas = umbralDeFallas;
		this.esperaAbiertoMs = esperaAbierto.toMillis();
		this.esFallaDelServicio = esFallaDelServicio;
		this.reloj = reloj;
	}

	/** Ejecuta la llamada protegida por el circuito. . */
	public <T> T ejecutar(Supplier<T> llamada) {
		boolean esPrueba = pedirPaso();
		try {
			T resultado = llamada.get();
			registrarExito(esPrueba);
			return resultado;
		} catch (Throwable error) {
			if (esFalla(error)) {
				registrarFalla(esPrueba);
			} else {
				registrarExito(esPrueba); // error de negocio: el servicio si contesto
			}
			throw error;
		}
	}

	public void ejecutarSinResultado(Runnable llamada) {
		ejecutar(() -> {
			llamada.run();
			return null;
		});
	}
	public synchronized Map<String, Object> instantanea() {
		Map<String, Object> foto = new LinkedHashMap<>();
		foto.put("nombre", nombre);
		foto.put("estado", estado);
		foto.put("fallasSeguidas", fallasSeguidas);
		foto.put("umbralDeFallas", umbralDeFallas);
		foto.put("esperaAbiertoSegundos", esperaAbiertoMs / 1000.0);
		if (estado == Estado.ABIERTO) {
			foto.put("segundosParaProbar", Math.max(0, esperaAbiertoMs - (reloj.getAsLong() - abiertoDesdeMs)) / 1000.0);
		}
		return foto;
	}

	public synchronized Estado estado() {
		return estado;
	}

	// Decide si la llamada puede pasar. Devuelve true si es la llamada de PRUEBA (semiabierto).
	private synchronized boolean pedirPaso() {
		if (estado == Estado.ABIERTO) {
			long transcurrido = reloj.getAsLong() - abiertoDesdeMs;
			if (transcurrido < esperaAbiertoMs) {
				throw new CircuitoAbiertoException("El circuito '" + nombre + "' esta abierto: se rechaza la llamada sin "
					+ "intentarla (se volvera a probar en " + Math.max(1, (esperaAbiertoMs - transcurrido + 999) / 1000) + " s)");
			}
			cambiarA(Estado.SEMIABIERTO);
			pruebaEnCurso = false;
		}
		if (estado == Estado.SEMIABIERTO) {
			if (pruebaEnCurso) {
				throw new CircuitoAbiertoException("El circuito '" + nombre + "' esta semiabierto y ya hay una llamada de "
					+ "prueba en curso: se rechaza esta llamada");
			}
			pruebaEnCurso = true;
			return true;
		}
		return false;
	}

	private synchronized void registrarExito(boolean esPrueba) {
		if (esPrueba) {
			pruebaEnCurso = false;
			if (estado == Estado.SEMIABIERTO) {
				fallasSeguidas = 0;
				cambiarA(Estado.CERRADO);
			}
		} else if (estado == Estado.CERRADO) {
			fallasSeguidas = 0;
		}
	}

	private synchronized void registrarFalla(boolean esPrueba) {
		if (esPrueba) {
			pruebaEnCurso = false;
			if (estado == Estado.SEMIABIERTO) {
				abrir();
			}
		} else if (estado == Estado.CERRADO) {
			fallasSeguidas++;
			if (fallasSeguidas >= umbralDeFallas) {
				abrir();
			}
		}
	}

	private void abrir() {
		abiertoDesdeMs = reloj.getAsLong();
		cambiarA(Estado.ABIERTO);
	}

	private void cambiarA(Estado nuevo) {
		log.warn("Circuit breaker '{}': {} -> {} (fallas seguidas: {})", nombre, estado, nuevo, fallasSeguidas);
		estado = nuevo;
	}

	private boolean esFalla(Throwable error) {
		return !(error instanceof RuntimeException runtime) || esFallaDelServicio.test(runtime);
	}
}