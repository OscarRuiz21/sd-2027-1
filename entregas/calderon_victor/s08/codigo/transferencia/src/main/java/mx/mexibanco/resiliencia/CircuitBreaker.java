package mx.mexibanco.resiliencia;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Lab S08: circuit breaker hecho a mano, con los tres estados de la clase.
 *
 *   CERRADO      pasan todas las llamadas y se cuentan las fallas seguidas. Al llegar al umbral: ABIERTO.
 *   ABIERTO      no se llama a nadie: se falla al instante con CircuitoAbiertoException. Cuando pasa
 *                la espera, la siguiente llamada lo pone en SEMIABIERTO.
 *   SEMIABIERTO  pasa UNA llamada de prueba (las demas fallan al instante). Si sale bien: CERRADO;
 *                si falla: ABIERTO otra vez, con la espera desde cero.
 *
 * Solo cuenta como falla lo que dice que el otro servicio esta mal (no contesta, timeout, 5xx). Un
 * 404 o un 422 es cuenta contestando bien que no se puede: eso no abre el circuito.
 */
public class CircuitBreaker {

	public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }

	private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);
	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final int CAMBIOS_QUE_SE_GUARDAN = 20;

	private final String nombre;
	private final int umbral;
	private final Duration espera;
	private final Predicate<RuntimeException> esFalla;
	private final Clock reloj;

	private Estado estado = Estado.CERRADO;
	private int fallasSeguidas;
	private Instant abiertoDesde;
	private boolean pruebaEnCurso;
	private String ultimaFalla;
	private final Deque<String> cambios = new ArrayDeque<>();

	public CircuitBreaker(String nombre, int umbral, Duration espera, Predicate<RuntimeException> esFalla, Clock reloj) {
		this.nombre = nombre;
		this.umbral = umbral;
		this.espera = espera;
		this.esFalla = esFalla;
		this.reloj = reloj;
	}

	/** La regla para los servicios de Mexi Banco: falla es lo que no es una respuesta 4xx del otro servicio. */
	public static boolean esFallaDelServicio(RuntimeException e) {
		return !(e instanceof ErrorRemotoException remoto) || remoto.getEstado() >= 500;
	}

	/** Hace la llamada si el circuito la deja pasar, y anota si salio bien o mal. */
	public <T> T llamar(Supplier<T> llamada) {
		antesDeLlamar();
		try {
			T resultado = llamada.get();
			alSalirBien();
			return resultado;
		} catch (RuntimeException e) {
			if (esFalla.test(e)) {
				alFallar(e);
			} else {
				alSalirBien(); // el servicio contesto, aunque sea que no: esta vivo
			}
			throw e;
		}
	}

	private synchronized void antesDeLlamar() {
		if (estado == Estado.ABIERTO) {
			Duration falta = Duration.between(reloj.instant(), abiertoDesde.plus(espera));
			if (falta.isPositive()) {
				throw new CircuitoAbiertoException(nombre, "se vuelve a probar en " + segundos(falta) + " s; ultima falla: " + ultimaFalla);
			}
			cambiarA(Estado.SEMIABIERTO, "pasaron " + espera.toSeconds() + " s: se deja pasar una llamada de prueba");
		}
		if (estado == Estado.SEMIABIERTO) {
			if (pruebaEnCurso) {
				throw new CircuitoAbiertoException(nombre, "ya hay una llamada de prueba en curso");
			}
			pruebaEnCurso = true;
		}
	}

	private synchronized void alSalirBien() {
		if (estado == Estado.SEMIABIERTO) {
			pruebaEnCurso = false;
			fallasSeguidas = 0;
			cambiarA(Estado.CERRADO, "la llamada de prueba salio bien");
		} else if (estado == Estado.CERRADO) {
			fallasSeguidas = 0;
		}
	}

	private synchronized void alFallar(RuntimeException e) {
		ultimaFalla = e.getMessage();
		if (estado == Estado.SEMIABIERTO) {
			pruebaEnCurso = false;
			abrir("la llamada de prueba fallo: " + e.getMessage());
		} else if (estado == Estado.CERRADO) {
			fallasSeguidas++;
			log.warn("circuito {}: falla {} de {} ({})", nombre, fallasSeguidas, umbral, e.getMessage());
			if (fallasSeguidas >= umbral) {
				abrir(fallasSeguidas + " fallas seguidas");
			}
		}
	}

	private void abrir(String porque) {
		abiertoDesde = reloj.instant();
		cambiarA(Estado.ABIERTO, porque + "; no se llama a " + nombre + " en " + espera.toSeconds() + " s");
	}

	private void cambiarA(Estado nuevo, String porque) {
		Estado anterior = estado;
		estado = nuevo;
		cambios.addLast(HORA.format(reloj.instant().atZone(reloj.getZone())) + "  " + anterior + " -> " + nuevo + "  (" + porque + ")");
		if (cambios.size() > CAMBIOS_QUE_SE_GUARDAN) {
			cambios.removeFirst();
		}
		log.warn("circuito {}: {} -> {} ({})", nombre, anterior, nuevo, porque);
	}

	/** Lo que muestra /actuator/circuitbreakers. */
	public synchronized Map<String, Object> foto() {
		Map<String, Object> foto = new LinkedHashMap<>();
		foto.put("estado", estado);
		foto.put("fallasSeguidas", fallasSeguidas);
		foto.put("umbral", umbral);
		foto.put("esperaAbiertoSegundos", espera.toSeconds());
		if (estado == Estado.ABIERTO) {
			Duration falta = Duration.between(reloj.instant(), abiertoDesde.plus(espera));
			foto.put("seVuelveAProbarEnSegundos", falta.isPositive() ? segundos(falta) : 0);
		}
		if (ultimaFalla != null) {
			foto.put("ultimaFalla", ultimaFalla);
		}
		foto.put("cambios", List.copyOf(cambios));
		return foto;
	}

	public String getNombre() {
		return nombre;
	}

	public synchronized Estado getEstado() {
		return estado;
	}

	private static long segundos(Duration d) {
		return (d.toMillis() + 999) / 1000;
	}
}
