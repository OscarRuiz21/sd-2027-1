package mx.mexibanco.notificacion;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tarea S09: la marca de "este evento ya lo procese". El eventoId es la llave primaria, asi que la
 * base deja entrar una sola fila por evento aunque el broker lo entregue dos veces (at-least-once).
 * Se escribe con EventoProcesadoRepository.marcarSiEsNuevo, en la misma transaccion que los avisos.
 */
@Entity
public class EventoProcesado {

	@Id
	private UUID eventoId;

	@Column(nullable = false, length = 40)
	private String tipo;

	@Column(nullable = false)
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
