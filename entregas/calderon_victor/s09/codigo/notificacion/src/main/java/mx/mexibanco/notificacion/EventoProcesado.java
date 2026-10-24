package mx.mexibanco.notificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

/**
 * Lab S09, Parte 3: la marca de "este evento ya lo procese". Una fila por eventoId, que es la llave
 * primaria: la base no deja que haya dos. Se escribe en la MISMA transaccion que los avisos del
 * evento, asi que existe la marca si y solo si existen sus avisos.
 *
 * La tabla la crea ddl-auto a partir de esta clase; las filas se insertan con
 * EventoProcesadoRepository.marcarSiEsNuevo (INSERT ... ON CONFLICT DO NOTHING), no con save().
 */
@Entity
public class EventoProcesado {

	@Id
	private UUID eventoId;

	@Column(nullable = false, length = 40)
	private String tipo;

	@Column
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
