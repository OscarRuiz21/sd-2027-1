package mx.mexibanco.transferencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Termina las sagas que se quedaron en COMPENSACION_PENDIENTE, con ESPERA CRECIENTE (backoff
 * exponencial): tras el intento n, el siguiente toca base * 2^(n-1), con tope en `max`.
 * Con base 5 s y tope 60 s: 5, 10, 20, 40, 60, 60, ... Asi no golpeamos a cuenta cada pocos segundos
 * durante una caida larga. No hace falta guardar "proximo intento": se calcula con `actualizada`
 * (cuando fue el ultimo intento) y `intentosCompensacion`.
 *
 * Reintentar es seguro porque compensar() es idempotente: el abono de reverso lleva la clave
 * "reverso-{id}" y cuenta no lo aplica dos veces. Sin eso, cada reintento devolveria el dinero otra vez.
 *
 * Con varias replicas de transferencia haria falta un candado sobre la fila
 * (SELECT ... FOR UPDATE SKIP LOCKED, o ShedLock) para que dos replicas no tomen la misma.
 */
@Component
@EnableScheduling
public class ReintentoDeCompensaciones {

	private static final Logger log = LoggerFactory.getLogger(ReintentoDeCompensaciones.class);

	private final TransferenciaRepository transferencias;
	private final TransferenciaService servicio;
	private final Duration base;
	private final Duration maximo;

	public ReintentoDeCompensaciones(TransferenciaRepository transferencias, TransferenciaService servicio,
									 @Value("${mexibanco.saga.reintento-base-ms:5000}") long baseMs,
									 @Value("${mexibanco.saga.reintento-max-ms:60000}") long maximoMs) {
		this.transferencias = transferencias;
		this.servicio = servicio;
		this.base = Duration.ofMillis(baseMs);
		this.maximo = Duration.ofMillis(maximoMs);
	}

	/** Revisa seguido, pero solo reintenta las que ya cumplieron su espera. */
	@Scheduled(fixedDelayString = "${mexibanco.saga.revision-ms:2000}",
		initialDelayString = "${mexibanco.saga.revision-ms:2000}")
	public void reintentar() {
		List<Transferencia> pendientes = transferencias.findByEstado(EstadoTransferencia.COMPENSACION_PENDIENTE);
		Instant ahora = Instant.now();
		for (Transferencia t : pendientes) {
			Duration espera = esperaTras(t.getIntentosCompensacion());
			if (ahora.isBefore(t.getActualizada().plus(espera))) {
				continue; // todavia no le toca
			}
			log.info("Reintentando la compensacion de la transferencia {} (intentos previos: {}, espera aplicada: {} ms)",
				t.getId(), t.getIntentosCompensacion(), espera.toMillis());
			try {
				servicio.compensar(t);
			} catch (RuntimeException e) {
				// compensar() no lanza por fallas de red; esto seria la base de transferencia caida.
				log.error("No se pudo guardar el reintento de la transferencia {}", t.getId(), e);
			}
		}
	}

	/** base * 2^(intentos-1), con tope. */
	Duration esperaTras(int intentos) {
		int exponente = Math.max(0, Math.min(intentos - 1, 20)); // 2^20 ya rebasa cualquier tope razonable
		Duration espera = base.multipliedBy(1L << exponente);
		return espera.compareTo(maximo) > 0 ? maximo : espera;
	}
}
