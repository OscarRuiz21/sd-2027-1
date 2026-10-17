package mx.mexibanco.transferencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Lab S08: termina las transferencias en COMPENSACION_PENDIENTE. Cada segundo busca las devoluciones
 * a las que ya les toca su siguiente intento y las vuelve a intentar; si fallan otra vez, compensar()
 * les pone una espera mas larga (2, 4, 8, 16... hasta 30 s). Como lee el estado de la base, tambien
 * retoma las pendientes que quedaron de antes de un reinicio.
 */
@Component
class ReintentoDeCompensaciones {

	private static final Logger log = LoggerFactory.getLogger(ReintentoDeCompensaciones.class);

	private final TransferenciaRepository transferencias;
	private final TransferenciaService saga;

	ReintentoDeCompensaciones(TransferenciaRepository transferencias, TransferenciaService saga) {
		this.transferencias = transferencias;
		this.saga = saga;
	}

	@Scheduled(fixedDelayString = "${mexibanco.saga.revisar-cada-ms:1000}")
	void reintentarPendientes() {
		for (Transferencia t : transferencias.findByEstadoAndProximoIntentoLessThanEqualOrderByIdAsc(
			EstadoTransferencia.COMPENSACION_PENDIENTE, Instant.now())) {
			log.info("transferencia {}: reintento programado de la devolucion (intento {})", t.getId(),
				t.getIntentosCompensacion() + 1);
			try {
				saga.compensar(t);
			} catch (OptimisticLockingFailureException otroProcesoLaTomo) {
				log.info("transferencia {}: otro proceso ya la estaba devolviendo", t.getId());
			}
		}
	}
}
