package mx.mexibanco.clientes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CircuitoCuenta {

	private enum Estado { CERRADO, ABIERTO, SEMIABIERTO }

	private static final Logger LOG = LoggerFactory.getLogger(CircuitoCuenta.class);
	private static final int LIMITE = 3;
	private static final long ESPERA = Duration.ofSeconds(10).toNanos();

	private Estado estado = Estado.CERRADO;
	private int fallas;
	private long apertura;
	private long turno;

	// Cada cambio de estado invalida las respuestas de llamadas anteriores.
	synchronized long permitir() {
		if (estado == Estado.CERRADO) {
			return turno;
		}
		if (estado == Estado.ABIERTO && System.nanoTime() - apertura >= ESPERA) {
			cambiarA(Estado.SEMIABIERTO);
			return turno;
		}
		throw new FallaCuenta("Circuito de cuenta " + estado + "; no se envió la solicitud", false, null);
	}

	synchronized void registrar(long permiso, boolean respondioBien) {
		if (permiso != turno) {
			return;
		}
		if (respondioBien) {
			fallas = 0;
			if (estado == Estado.SEMIABIERTO) {
				cambiarA(Estado.CERRADO);
			}
		} else if (estado == Estado.SEMIABIERTO || ++fallas >= LIMITE) {
			apertura = System.nanoTime();
			cambiarA(Estado.ABIERTO);
		}
	}

	private void cambiarA(Estado siguiente) {
		LOG.info("Circuito cuenta: {} -> {}", estado, siguiente);
		estado = siguiente;
		turno++;
	}
}
