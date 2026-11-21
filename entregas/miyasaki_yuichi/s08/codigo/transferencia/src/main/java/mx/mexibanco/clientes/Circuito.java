package mx.mexibanco.clientes;

import mx.mexibanco.compartido.CircuitoAbiertoException;
import mx.mexibanco.compartido.ErrorRemotoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Circuit breaker hecho a mano (S08) para las llamadas a otro servicio. Tres estados:
 *
 * CERRADO: las llamadas pasan y se cuentan las fallas seguidas. Al llegar al umbral se abre.
 * ABIERTO: se rechaza todo de inmediato, sin llamar al servicio, durante esperaMs.
 * SEMIABIERTO: pasada la espera se deja pasar UNA llamada de prueba. Si sale bien se cierra; si
 *              falla se vuelve a abrir y empieza otra espera.
 *
 * Solo cuentan como falla los errores tecnicos (no conecta, timeout, sin instancias, 5xx). Un 404
 * o un 422 es una respuesta correcta del servicio: si contaran, una CLABE mal escrita abriria el circuito.
 */
public class Circuito {

	public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }

	private static final Logger log = LoggerFactory.getLogger(Circuito.class);

	private final String servicio;
	private final int umbral;
	private final long esperaMs;
	private final LongSupplier reloj;

	private Estado estado = Estado.CERRADO;
	private int fallasSeguidas;
	private long abiertoDesde;
	private boolean pruebaEnCurso;

	public Circuito(String servicio, int umbral, long esperaMs, LongSupplier reloj) {
		this.servicio = servicio;
		this.umbral = umbral;
		this.esperaMs = esperaMs;
		this.reloj = reloj;
	}

	public <T> T ejecutar(Supplier<T> llamada) {
		permitir();
		try {
			T respuesta = llamada.get();
			exito();
			return respuesta;
		} catch (RuntimeException e) {
			if (esFallaTecnica(e)) {
				falla(e);
			} else {
				exito(); // el servicio contesto, aunque fuera con un error de negocio
			}
			throw e;
		}
	}

	/** Decide si la llamada pasa. El candado solo cubre esta decision, no la llamada por la red. */
	private synchronized void permitir() {
		if (estado == Estado.ABIERTO) {
			long transcurrido = reloj.getAsLong() - abiertoDesde;
			if (transcurrido < esperaMs) {
				throw new CircuitoAbiertoException(servicio, esperaMs - transcurrido);
			}
			cambiarA(Estado.SEMIABIERTO);
			pruebaEnCurso = true;
			return;
		}
		if (estado == Estado.SEMIABIERTO && pruebaEnCurso) {
			// Ya hay una llamada de prueba en vuelo: las demas esperan su resultado afuera.
			throw new CircuitoAbiertoException(servicio, 0);
		}
	}

	private synchronized void exito() {
		fallasSeguidas = 0;
		if (estado == Estado.SEMIABIERTO) {
			pruebaEnCurso = false;
			cambiarA(Estado.CERRADO);
		}
	}

	private synchronized void falla(RuntimeException e) {
		if (estado == Estado.SEMIABIERTO) {
			pruebaEnCurso = false;
			abrir("fallo la llamada de prueba: " + e.getMessage());
			return;
		}
		if (estado == Estado.ABIERTO) {
			return; // una llamada que salio antes de abrirse; ya no cambia nada
		}
		fallasSeguidas++;
		log.warn("Circuito {}: falla {} de {} ({})", servicio, fallasSeguidas, umbral, e.getMessage());
		if (fallasSeguidas >= umbral) {
			abrir(fallasSeguidas + " fallas seguidas");
		}
	}

	private void abrir(String motivo) {
		abiertoDesde = reloj.getAsLong();
		cambiarA(Estado.ABIERTO);
		log.warn("Circuito {}: abierto por {}; se rechaza todo durante {} ms", servicio, motivo, esperaMs);
	}

	private void cambiarA(Estado nuevo) {
		log.warn("Circuito {}: {} -> {}", servicio, estado, nuevo);
		estado = nuevo;
	}

	static boolean esFallaTecnica(RuntimeException e) {
		if (e instanceof ErrorRemotoException remoto) {
			return remoto.getEstado() >= 500;
		}
		// ResourceAccessException: no conecta o timeout. IllegalStateException: el balanceador no
		// encontro instancias ("No instances available for cuenta").
		return e instanceof ResourceAccessException || e instanceof IllegalStateException;
	}

	public synchronized Estado getEstado() {
		return estado;
	}

	public synchronized int getFallasSeguidas() {
		return fallasSeguidas;
	}

	public String getServicio() {
		return servicio;
	}
}
