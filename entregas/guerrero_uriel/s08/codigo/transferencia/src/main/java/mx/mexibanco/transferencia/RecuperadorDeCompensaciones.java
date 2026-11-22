package mx.mexibanco.transferencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Termina las compensaciones pendientes. Seguro de repetir: la compensación es idempotente. */
@Component
@EnableScheduling
class RecuperadorDeCompensaciones {

	private static final Logger log = LoggerFactory.getLogger(RecuperadorDeCompensaciones.class);
	private static final int MAX_INTENTOS = 10;

	private final TransferenciaRepository transferencias;
	private final TransferenciaService servicio;

	RecuperadorDeCompensaciones(TransferenciaRepository transferencias, TransferenciaService servicio) {
		this.transferencias = transferencias;
		this.servicio = servicio;
	}

	@Scheduled(fixedDelay = 15_000)
	void reintentar() {
		if (!servicio.cuentaDisponible()) {
			log.info("Circuito de cuenta abierto: se pospone el reintento de compensaciones");
			return; // no gastar intentos mientras cuenta no responde
		}
		Instant limite = Instant.now().minusSeconds(30);
		for (Transferencia t : transferencias.findByEstadoAndActualizadaBefore(
				EstadoTransferencia.COMPENSACION_PENDIENTE, limite)) {
			if (t.getIntentosCompensacion() >= MAX_INTENTOS) {
				log.error("Transferencia {} agotó {} intentos de compensación: requiere intervención", t.getId(), MAX_INTENTOS);
				continue;
			}
			servicio.compensar(t, "Reintento programado");
		}
	}
}