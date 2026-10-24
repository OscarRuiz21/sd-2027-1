package mx.mexibanco.notificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {

	@Id
	@Column(name = "evento_id")
	private UUID eventoId;

	@Column(name = "tipo", nullable = false, length = 40)
	private String tipo;

	@Column(name = "transferencia_id")
	private Long transferenciaId;

	@Column(name = "procesado_en", nullable = false)
	private Instant procesadoEn;

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

	public Instant getProcesadoEn() {
		return procesadoEn;
	}
}