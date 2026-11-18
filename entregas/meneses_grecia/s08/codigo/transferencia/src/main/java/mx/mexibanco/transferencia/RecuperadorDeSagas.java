package mx.mexibanco.transferencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

/**
 * S08: la "red de seguridad" de la saga. Cada SAGA_RECUPERACION_MS revisa la tabla y:
 *
 *  - Reintenta las COMPENSACION_PENDIENTE cuyo proximoIntento ya llego (la espera crece:
 *    2 s, 4 s, 8 s, ... hasta SAGA_REINTENTO_MAXIMO_MS).
 *  - Reanuda las sagas que se quedaron en INICIADA, CARGADA o ABONADA sin moverse durante
 *    SAGA_ABANDONADA_MS (el orquestador se cayo a la mitad): las de ABONADA se terminan, las otras
 *    se compensan.
 *
 * Reintentar es seguro solo porque la compensacion es IDEMPOTENTE (Idempotency-Key en cuenta):
 * si un intento anterior si devolvio el dinero pero se perdio la respuesta, el siguiente no lo
 * devuelve otra vez.
 *
 * Supuesto: hay UNA instancia de transferencia. Con varias, dos recuperadores podrian tomar la misma
 * fila; @Version en Transferencia hace que solo uno pueda guardar, y la llave de idempotencia evita
 * que el dinero se mueva dos veces.
 */
@Component
public class RecuperadorDeSagas {

	private static final Logger log = LoggerFactory.getLogger(RecuperadorDeSagas.class);

	private final TransferenciaRepository transferencias;
	private final TransferenciaService servicio;
	private final Duration abandonada;

	public RecuperadorDeSagas(TransferenciaRepository transferencias, TransferenciaService servicio,
							  @Value("${mexibanco.saga.abandonada-ms:60000}") long abandonadaMs) {
		this.transferencias = transferencias;
		this.servicio = servicio;
		this.abandonada = Duration.ofMillis(abandonadaMs);
	}

	@Scheduled(initialDelayString = "${mexibanco.saga.recuperacion-ms:5000}",
		fixedDelayString = "${mexibanco.saga.recuperacion-ms:5000}")
	public void recuperar() {
		Instant ahora = Instant.now();
		for (Transferencia t : transferencias.findByEstadoAndProximoIntentoLessThanEqual(
			EstadoTransferencia.COMPENSACION_PENDIENTE, ahora)) {
			intentar(t, "reintento de compensacion #" + (t.getIntentosCompensacion() + 1), () -> servicio.compensar(t));
		}
		for (Transferencia t : transferencias.findByEstadoInAndActualizadaBefore(
			EnumSet.of(EstadoTransferencia.INICIADA, EstadoTransferencia.CARGADA, EstadoTransferencia.ABONADA),
			ahora.minus(abandonada))) {
			intentar(t, "saga abandonada en " + t.getEstado(), () -> servicio.reanudar(t));
		}
	}

	private void intentar(Transferencia t, String que, java.util.function.Supplier<Transferencia> accion) {
		try {
			log.info("Recuperador: transferencia #{} ({})", t.getId(), que);
			Transferencia resultado = accion.get();
			log.info("Recuperador: transferencia #{} quedo en {}", t.getId(), resultado.getEstado());
		} catch (RuntimeException e) {
			// p. ej. OptimisticLockingFailureException: otro hilo la tomo primero. Se revisa en la siguiente vuelta.
			log.warn("Recuperador: transferencia #{} no se pudo procesar: {}", t.getId(), e.getMessage());
		}
	}
}
