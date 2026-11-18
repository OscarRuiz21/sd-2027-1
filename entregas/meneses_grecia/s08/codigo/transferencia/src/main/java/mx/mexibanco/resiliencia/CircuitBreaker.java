package mx.mexibanco.resiliencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * S08: circuit breaker hecho a mano (sin Resilience4j), con sus tres estados.
 *
 * <pre>
 *            N fallas seguidas                 paso el tiempo de espera
 *   CERRADO ------------------> ABIERTO ----------------------------> SEMIABIERTO
 *      ^                          ^                                      |   |
 *      |                          +------ la llamada de prueba falla ----+   |
 *      +------------------ M llamadas de prueba exitosas --------------------+
 * </pre>
 *
 * CERRADO: todas las llamadas pasan; se cuentan las fallas SEGUIDAS (un exito pone el contador en 0).
 * ABIERTO: ninguna llamada pasa; se responde de inmediato con CircuitoAbiertoException, sin tocar
 *          el servicio remoto. Asi no lo golpeamos mientras se levanta ni nos quedamos colgados.
 * SEMIABIERTO: pasa UNA llamada de prueba a la vez. Si sale bien M veces seguidas, se cierra; si
 *          falla, se vuelve a abrir y se espera otra vez.
 *
 * Que cuenta como falla lo decide quien lo usa (esFalla): un 404 o un 422 de cuenta son respuestas
 * de negocio, cuenta esta viva; un 5xx, un timeout o "no hay instancias" si son fallas.
 *
 * Es seguro entre hilos: el estado solo se cambia dentro de metodos synchronized; la llamada
 * remota se hace FUERA del candado para no serializar todas las peticiones.
 */
public class CircuitBreaker {

	public enum Estado {
		CERRADO,
		ABIERTO,
		SEMIABIERTO
	}

	public record Transicion(Instant cuando, Estado de, Estado a, String motivo) {
	}

	private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);
	private static final int HISTORIAL_MAXIMO = 50;

	private final String nombre;
	private final int umbralFallas;
	private final Duration espera;
	private final int pruebasParaCerrar;
	private final Predicate<Throwable> esFalla;
	private final Clock reloj;

	private Estado estado = Estado.CERRADO;
	private int fallasSeguidas;
	private int exitosEnPrueba;
	private boolean pruebaEnCurso;
	private Instant abiertoHasta;
	private String ultimaFalla;
	private long llamadasPermitidas;
	private long llamadasRechazadas;
	private final Deque<Transicion> historial = new ArrayDeque<>();

	public CircuitBreaker(String nombre, int umbralFallas, Duration espera, int pruebasParaCerrar,
						  Predicate<Throwable> esFalla, Clock reloj) {
		if (umbralFallas < 1 || pruebasParaCerrar < 1 || espera.isNegative()) {
			throw new IllegalArgumentException("Configuracion invalida del circuito " + nombre);
		}
		this.nombre = nombre;
		this.umbralFallas = umbralFallas;
		this.espera = espera;
		this.pruebasParaCerrar = pruebasParaCerrar;
		this.esFalla = esFalla;
		this.reloj = reloj;
	}

	/** Ejecuta la llamada protegida por el circuito. Las excepciones de la llamada se relanzan tal cual. */
	public <T> T ejecutar(Supplier<T> llamada) {
		Estado concedidoEn = pedirPermiso();
		T resultado;
		try {
			resultado = llamada.get();
		} catch (RuntimeException e) {
			if (esFalla.test(e)) {
				registrarFalla(concedidoEn, e);
			} else {
				registrarExito(concedidoEn);
			}
			throw e;
		}
		registrarExito(concedidoEn);
		return resultado;
	}

	public synchronized Estado estado() {
		revisarTiempoDeEspera();
		return estado;
	}

	/** Foto del circuito para el endpoint GET /transferencias/circuito y para la bitacora. */
	public synchronized Map<String, Object> resumen() {
		revisarTiempoDeEspera();
		Map<String, Object> foto = new LinkedHashMap<>();
		foto.put("nombre", nombre);
		foto.put("estado", estado);
		foto.put("fallasSeguidas", fallasSeguidas);
		foto.put("umbralFallas", umbralFallas);
		foto.put("esperaMs", espera.toMillis());
		foto.put("pruebasParaCerrar", pruebasParaCerrar);
		foto.put("exitosEnPrueba", exitosEnPrueba);
		foto.put("abiertoHasta", estado == Estado.ABIERTO ? abiertoHasta : null);
		foto.put("ultimaFalla", ultimaFalla);
		foto.put("llamadasPermitidas", llamadasPermitidas);
		foto.put("llamadasRechazadas", llamadasRechazadas);
		List<Transicion> transiciones = new ArrayList<>(historial);
		foto.put("transiciones", transiciones);
		return foto;
	}

	private synchronized Estado pedirPermiso() {
		revisarTiempoDeEspera();
		switch (estado) {
			case CERRADO -> {
				llamadasPermitidas++;
				return Estado.CERRADO;
			}
			case SEMIABIERTO -> {
				if (pruebaEnCurso) {
					llamadasRechazadas++;
					throw new CircuitoAbiertoException(nombre, estado, 0,
						"El circuito hacia " + nombre + " esta SEMIABIERTO y ya hay una llamada de prueba en curso");
				}
				pruebaEnCurso = true;
				llamadasPermitidas++;
				return Estado.SEMIABIERTO;
			}
			default -> {
				llamadasRechazadas++;
				long faltanMs = Math.max(0, Duration.between(ahora(), abiertoHasta).toMillis());
				throw new CircuitoAbiertoException(nombre, estado, faltanMs,
					"El circuito hacia " + nombre + " esta ABIERTO: no se llamo al servicio. Se volvera a probar en "
						+ faltanMs + " ms. Ultima falla: " + ultimaFalla);
			}
		}
	}

	private synchronized void registrarExito(Estado concedidoEn) {
		if (concedidoEn == Estado.SEMIABIERTO && estado == Estado.SEMIABIERTO) {
			pruebaEnCurso = false;
			exitosEnPrueba++;
			if (exitosEnPrueba >= pruebasParaCerrar) {
				cambiarA(Estado.CERRADO, exitosEnPrueba + " llamadas de prueba exitosas");
			}
		} else if (estado == Estado.CERRADO) {
			fallasSeguidas = 0;
		}
		// Si estaba ABIERTO, es una llamada que salio antes de abrirse: no cambia nada.
	}

	private synchronized void registrarFalla(Estado concedidoEn, Throwable e) {
		ultimaFalla = e.getClass().getSimpleName() + ": " + e.getMessage();
		if (concedidoEn == Estado.SEMIABIERTO && estado == Estado.SEMIABIERTO) {
			pruebaEnCurso = false;
			abrir("fallo la llamada de prueba (" + ultimaFalla + ")");
		} else if (estado == Estado.CERRADO) {
			fallasSeguidas++;
			log.info("Circuito {}: falla {} de {} ({})", nombre, fallasSeguidas, umbralFallas, ultimaFalla);
			if (fallasSeguidas >= umbralFallas) {
				abrir(fallasSeguidas + " fallas seguidas");
			}
		}
	}

	private void abrir(String motivo) {
		abiertoHasta = ahora().plus(espera);
		cambiarA(Estado.ABIERTO, motivo);
	}

	/** El paso de ABIERTO a SEMIABIERTO no necesita un hilo: se revisa cada vez que alguien pregunta. */
	private void revisarTiempoDeEspera() {
		if (estado == Estado.ABIERTO && !ahora().isBefore(abiertoHasta)) {
			cambiarA(Estado.SEMIABIERTO, "pasaron " + espera.toMillis() + " ms abierto");
		}
	}

	private void cambiarA(Estado nuevo, String motivo) {
		Estado anterior = estado;
		estado = nuevo;
		fallasSeguidas = 0;
		exitosEnPrueba = 0;
		pruebaEnCurso = false;
		historial.addLast(new Transicion(ahora(), anterior, nuevo, motivo));
		while (historial.size() > HISTORIAL_MAXIMO) {
			historial.removeFirst();
		}
		log.warn("Circuito {}: {} -> {} ({})", nombre, anterior, nuevo, motivo);
	}

	private Instant ahora() {
		return reloj.instant();
	}
}
