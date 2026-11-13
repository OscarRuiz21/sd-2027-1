package mx.mexibanco.transferencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Busca pendientes periodicamente; no toca operaciones viejas sin llave idempotente. */
@Component
public class RecuperacionCompensaciones {

	private static final Logger log = LoggerFactory.getLogger(RecuperacionCompensaciones.class);
	private final TransferenciaRepository transferencias;
	private final TransferenciaService servicio;
	private final boolean habilitada;

	public RecuperacionCompensaciones(TransferenciaRepository transferencias, TransferenciaService servicio,
			@Value("${mexibanco.compensacion.automatica:false}") boolean habilitada) {
		this.transferencias = transferencias;
		this.servicio = servicio;
		this.habilitada = habilitada;
	}

	@Scheduled(fixedDelayString = "${mexibanco.compensacion.revision-ms:5000}")
	public void revisar() {
		if (!habilitada) {
			return;
		}
		List<Transferencia> pendientes = transferencias
			.findByEstadoInAndLlaveCompensacionIsNotNullAndProximoReintentoLessThanEqualOrderByProximoReintentoAsc(
				List.of(EstadoTransferencia.COMPENSACION_PENDIENTE, EstadoTransferencia.COMPENSANDO), Instant.now());
		for (Transferencia pendiente : pendientes) {
			try {
				servicio.reintentarCompensacion(pendiente.getId());
			} catch (RuntimeException error) {
				log.warn("No se pudo reintentar la compensacion de {}", pendiente.getId(), error);
			}
		}
	}
}
