package mx.mexibanco.notificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * S09 (tarea del duplicado): un evento que notificacion YA proceso. El eventoId es la llave primaria,
 * asi que Postgres no deja que el mismo evento quede marcado dos veces. La marca se escribe en la MISMA
 * transaccion que los avisos (ver NotificacionService.avisarDeTransferencia).
 *
 * Solo se escribe con EventoProcesadoRepository.marcarSiEsNuevo; esta clase existe para que
 * ddl-auto=update cree la tabla.
 */
@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {

	@Id
	@Column(name = "evento_id", nullable = false)
	private UUID eventoId;

	@Column(nullable = false, length = 40)
	private String tipo;

	@Column(name = "transferencia_id")
	private Long transferenciaId;

	@Column(nullable = false)
	private Instant procesado;

	protected EventoProcesado() {
	}

	public UUID getEventoId() {
		return eventoId;
	}

	public String getTipo() {
		return tipo;
	}

	public Long getTransferenciaId() {
		return transferenciaId;
	}

	public Instant getProcesado() {
		return procesado;
	}
}
