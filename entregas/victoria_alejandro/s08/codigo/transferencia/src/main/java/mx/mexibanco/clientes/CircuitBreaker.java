package mx.mexibanco.clientes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Circuit breaker hecho a mano, sin Resilience4j. Protege las llamadas a un servicio remoto para
 * que, si esta caido o lento, no lo sigamos golpeando ni nos quedemos colgados esperandolo.
 *
 *   CERRADO     Todo pasa. Cuenta las fallas SEGUIDAS; un exito las regresa a 0.
 *               Al llegar a `umbralFallas` -> ABIERTO.
 *   ABIERTO     Nada pasa: cada llamada se rechaza al instante con CircuitoAbiertoException, sin
 *               tocar la red. Cuando se cumple `esperaAbierto` -> SEMIABIERTO.
 *   SEMIABIERTO Deja pasar UNA llamada de prueba (las demas se rechazan mientras esta en curso).
 *               Si sale bien -> CERRADO; si falla -> ABIERTO otra vez, y vuelve a correr la espera.
 *
 * Que cuenta como falla lo decide `esFalla`: una caida, un timeout o un 5xx si; un 404 o un 422 no,
 * porque ahi el servicio SI contesto (la peticion era la que estaba mal).
 *
 * Los cambios de estado se registran en el log con el prefijo "Circuito <nombre>": esa es la evidencia.
 * Es seguro entre hilos: el estado solo se toca dentro de metodos synchronized, y la llamada remota
 * se hace FUERA del candado para no bloquear a los demas hilos mientras dura.
 */
public class CircuitBreaker {

	public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }

	/** Foto del circuito para el endpoint de consulta. */
	public record Instantanea(String nombre, Estado estado, int fallasSeguidas, int umbralFallas,
							  long esperaAbiertoMs, Instant abiertoDesde, Instant pruebaPermitidaDesde,
							  String ultimaFalla) {
	}

	private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

	private final String nombre;
	private final int umbralFallas;
	private final Duration esperaAbierto;
	private final Predicate<Throwable> esFalla;
	private final Clock reloj;

	private Estado estado = Estado.CERRADO;
	private int fallasSeguidas;
	private Instant abiertoDesde;
	private boolean pruebaEnCurso;
	private String ultimaFalla;

	public CircuitBreaker(String nombre, int umbralFallas, Duration esperaAbierto, Predicate<Throwable> esFalla) {
		this(nombre, umbralFallas, esperaAbierto, esFalla, Clock.systemUTC());
	}

	/** Con reloj inyectable, para probarlo sin esperar de verdad. */
	public CircuitBreaker(String nombre, int umbralFallas, Duration esperaAbierto, Predicate<Throwable> esFalla, Clock reloj) {
		if (umbralFallas < 1) {
			throw new IllegalArgumentException("umbralFallas debe ser al menos 1");
		}
		this.nombre = nombre;
		this.umbralFallas = umbralFallas;
		this.esperaAbierto = esperaAbierto;
		this.esFalla = esFalla;
		this.reloj = reloj;
		log.info("Circuito {}: creado en CERRADO (umbral {} fallas seguidas, espera abierto {} ms)",
			nombre, umbralFallas, esperaAbierto.toMillis());
	}

	/** Ejecuta la llamada si el circuito lo permite; si no, lanza CircuitoAbiertoException sin llamarla. */
	public <T> T ejecutar(Supplier<T> llamada) {
		pedirPermiso();
		try {
			T resultado = llamada.get();
			registrarExito();
			return resultado;
		} catch (RuntimeException e) {
			if (esFalla.test(e)) {
				registrarFalla(e);
			} else {
				registrarExito(); // el servicio contesto, aunque fuera con un error de negocio
			}
			throw e;
		}
	}

	private synchronized void pedirPermiso() {
		if (estado == Estado.ABIERTO) {
			Duration restante = Duration.between(ahora(), abiertoDesde.plus(esperaAbierto));
			if (!restante.isNegative() && !restante.isZero()) {
				log.info("Circuito {}: ABIERTO, llamada rechazada sin tocar {} (faltan {} ms para probar)",
					nombre, nombre, restante.toMillis());
				throw new CircuitoAbiertoException(nombre, restante);
			}
			cambiarA(Estado.SEMIABIERTO, "pasaron " + esperaAbierto.toMillis() + " ms; se deja pasar una llamada de prueba");
		}
		if (estado == Estado.SEMIABIERTO) {
			if (pruebaEnCurso) {
				log.info("Circuito {}: SEMIABIERTO con prueba en curso, llamada rechazada", nombre);
				throw new CircuitoAbiertoException(nombre, Duration.ZERO);
			}
			pruebaEnCurso = true;
		}
	}

	private synchronized void registrarExito() {
		fallasSeguidas = 0;
		if (estado == Estado.SEMIABIERTO) {
			pruebaEnCurso = false;
			ultimaFalla = null;
			cambiarA(Estado.CERRADO, "la llamada de prueba salio bien");
		}
	}

	private synchronized void registrarFalla(RuntimeException e) {
		ultimaFalla = e.getClass().getSimpleName() + ": " + e.getMessage();
		switch (estado) {
			case SEMIABIERTO -> {
				pruebaEnCurso = false;
				abrir("fallo la llamada de prueba (" + ultimaFalla + ")");
			}
			case CERRADO -> {
				fallasSeguidas++;
				log.warn("Circuito {}: falla {}/{} en CERRADO ({})", nombre, fallasSeguidas, umbralFallas, ultimaFalla);
				if (fallasSeguidas >= umbralFallas) {
					abrir(fallasSeguidas + " fallas seguidas");
				}
			}
			case ABIERTO -> {
				// Una llamada que salio antes de abrirse y termino despues: ya estamos abiertos.
			}
		}
	}

	private void abrir(String motivo) {
		abiertoDesde = ahora();
		cambiarA(Estado.ABIERTO, motivo + "; se rechazan llamadas por " + esperaAbierto.toMillis() + " ms");
	}

	private void cambiarA(Estado nuevo, String motivo) {
		Estado anterior = estado;
		estado = nuevo;
		if (nuevo == Estado.CERRADO) {
			log.info("Circuito {}: {} -> {} ({})", nombre, anterior, nuevo, motivo);
		} else {
			log.warn("Circuito {}: {} -> {} ({})", nombre, anterior, nuevo, motivo);
		}
	}

	private Instant ahora() {
		return reloj.instant();
	}

	public synchronized Estado getEstado() {
		return estado;
	}

	public synchronized Instantanea instantanea() {
		Instant prueba = estado == Estado.ABIERTO ? abiertoDesde.plus(esperaAbierto) : null;
		return new Instantanea(nombre, estado, fallasSeguidas, umbralFallas, esperaAbierto.toMillis(),
			estado == Estado.CERRADO ? null : abiertoDesde, prueba, ultimaFalla);
	}
}
