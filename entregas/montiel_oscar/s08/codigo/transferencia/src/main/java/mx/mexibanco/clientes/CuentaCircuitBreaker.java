package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import java.util.function.Supplier;

/**
 * Circuit breaker pequeno para TODAS las llamadas de transferencia a cuenta. Solo errores de
 * disponibilidad (red, timeout o 5xx) cuentan; un 404 o 422 no significa que cuenta este caida.
 * Una sola peticion de prueba entra en HALF_OPEN, para evitar una estampida al recuperarse.
 */
@Component
public class CuentaCircuitBreaker {
	private static final Logger LOG = LoggerFactory.getLogger(CuentaCircuitBreaker.class);

	public enum Estado { CLOSED, OPEN, HALF_OPEN }

	private final int umbral;
	private final long esperaMs;
	private Estado estado = Estado.CLOSED;
	private int fallas;
	private long abiertoDesde;
	private boolean pruebaEnCurso;
	private long generacion;

	public CuentaCircuitBreaker(
			@Value("${mexibanco.circuito.umbral-fallas:3}") int umbral,
			@Value("${mexibanco.circuito.espera-ms:8000}") long esperaMs) {
		if (umbral < 1 || esperaMs < 1) throw new IllegalArgumentException("Configuracion invalida del circuito");
		this.umbral = umbral;
		this.esperaMs = esperaMs;
	}

	public <T> T ejecutar(Supplier<T> llamada) {
		boolean esPrueba;
		long generacionAlEntrar;
		synchronized (this) {
			if (estado == Estado.OPEN && (System.nanoTime() - abiertoDesde) / 1_000_000 >= esperaMs) {
				cambiarA(Estado.HALF_OPEN);
			}
			if (estado == Estado.OPEN || (estado == Estado.HALF_OPEN && pruebaEnCurso)) {
				LOG.info("CIRCUITO cuenta OPEN: llamada rechazada sin HTTP");
				throw new CircuitoAbiertoException();
			}
			esPrueba = estado == Estado.HALF_OPEN;
			if (esPrueba) pruebaEnCurso = true;
			generacionAlEntrar = generacion;
		}

		try {
			T resultado = llamada.get();
			exito(generacionAlEntrar, esPrueba);
			return resultado;
		} catch (RuntimeException error) {
			if (esFallaDeDisponibilidad(error)) falla(generacionAlEntrar);
			else exito(generacionAlEntrar, esPrueba); // 4xx: cuenta si respondio.
			if (sinInstancias(error)) {
				throw new CuentaSinInstanciasException();
			}
			throw error;
		}
	}

	private synchronized void exito(long generacionDeLlamada, boolean esPrueba) {
		// Una llamada antigua no puede cerrar el circuito que abrieron otras peticiones.
		if (generacionDeLlamada != generacion || estado == Estado.OPEN) return;
		if (estado == Estado.HALF_OPEN && !esPrueba) return;
		fallas = 0;
		pruebaEnCurso = false;
		if (estado != Estado.CLOSED) cambiarA(Estado.CLOSED);
	}

	private synchronized void falla(long generacionDeLlamada) {
		if (generacionDeLlamada != generacion || estado == Estado.OPEN) return;
		pruebaEnCurso = false;
		fallas++;
		LOG.warn("CIRCUITO cuenta {}: falla de disponibilidad {}/{}", estado, fallas, umbral);
		if (estado == Estado.HALF_OPEN || fallas >= umbral) {
			abiertoDesde = System.nanoTime();
			cambiarA(Estado.OPEN);
		}
	}

	private void cambiarA(Estado nuevo) {
		LOG.warn("CIRCUITO cuenta {} -> {}", estado, nuevo);
		if (nuevo == Estado.OPEN) generacion++;
		estado = nuevo;
	}

	private boolean esFallaDeDisponibilidad(RuntimeException error) {
		return error instanceof ResourceAccessException
				|| error instanceof ErrorRemotoException remoto && remoto.getEstado() >= 500
				|| sinInstancias(error);
	}

	private boolean sinInstancias(RuntimeException error) {
		return error instanceof IllegalStateException && error.getMessage() != null
				&& error.getMessage().startsWith("No instances available for cuenta");
	}

	public synchronized Estado estado() {
		return estado;
	}
}
