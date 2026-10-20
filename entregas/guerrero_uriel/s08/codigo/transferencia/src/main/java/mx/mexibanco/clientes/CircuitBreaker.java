package mx.mexibanco.clientes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.LongSupplier;

/**
 * Circuit breaker a mano, con tres estados:
 * CERRADO: las llamadas pasan; se cuentan las fallas seguidas.
 * ABIERTO: se rechazan sin llamar al servicio, durante esperaMs.
 * SEMIABIERTO: pasada la espera, deja pasar UNA llamada de prueba. Si sale bien, CERRADO; si falla, ABIERTO.
 */
public class CircuitBreaker {

	public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }

	private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

	private final String nombre;
	private final int umbral;
	private final long esperaMs;
	private final LongSupplier reloj;

	private Estado estado = Estado.CERRADO;
	private int fallasSeguidas;
	private long abiertoDesde;
	private boolean sondaEnCurso;

	public CircuitBreaker(String nombre, int umbral, long esperaMs) {
		this(nombre, umbral, esperaMs, System::currentTimeMillis);
	}

	CircuitBreaker(String nombre, int umbral, long esperaMs, LongSupplier reloj) {
		this.nombre = nombre;
		this.umbral = umbral;
		this.esperaMs = esperaMs;
		this.reloj = reloj;
	}

	/** Se llama antes de cada llamada remota. false = rechazarla sin hacerla. */
	public synchronized boolean permitir() {
		if (estado == Estado.CERRADO) {
			return true;
		}
		if (estado == Estado.ABIERTO) {
			if (reloj.getAsLong() - abiertoDesde >= esperaMs) {
				cambiar(Estado.SEMIABIERTO);
				sondaEnCurso = true;
				return true; // la llamada de prueba
			}
			return false;
		}
		// SEMIABIERTO: solo una sonda a la vez
		if (sondaEnCurso) {
			return false;
		}
		sondaEnCurso = true;
		return true;
	}

	public synchronized void exito() {
		if (estado == Estado.ABIERTO) {
			return; // una llamada que ya iba en vuelo cuando se abrio: no cierra el circuito
		}
		fallasSeguidas = 0;
		sondaEnCurso = false;
		if (estado != Estado.CERRADO) {
			cambiar(Estado.CERRADO);
		}
	}

	public synchronized void fallo() {
		sondaEnCurso = false;
		if (estado == Estado.SEMIABIERTO) {
			abrir();
		} else if (estado == Estado.CERRADO && ++fallasSeguidas >= umbral) {
			abrir();
		}
	}

	public synchronized Estado estado() {
		return estado;
	}

	/** true solo si de verdad rechazaria una llamada ahora (abierto y aun sin cumplir la espera). */
	public synchronized boolean estaAbierto() {
		return estado == Estado.ABIERTO && reloj.getAsLong() - abiertoDesde < esperaMs;
	}

	private void abrir() {
		abiertoDesde = reloj.getAsLong();
		fallasSeguidas = 0;
		cambiar(Estado.ABIERTO);
	}

	private void cambiar(Estado nuevo) {
		log.warn("Circuito '{}': {} -> {}", nombre, estado, nuevo);
		estado = nuevo;
	}
}