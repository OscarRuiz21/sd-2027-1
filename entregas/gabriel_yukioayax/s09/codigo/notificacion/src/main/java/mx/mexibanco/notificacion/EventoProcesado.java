package mx.mexibanco.notificacion;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * LAB S09: la marca de que un evento ya se proceso. El eventoId es la llave primaria, asi que la
 * base no deja guardar el mismo evento dos veces. Se escribe en la misma transaccion que los avisos.
 */
@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {

	@Id
	@Column(name = "evento_id")
	private UUID eventoId;

	@Column(nullable = false)
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
