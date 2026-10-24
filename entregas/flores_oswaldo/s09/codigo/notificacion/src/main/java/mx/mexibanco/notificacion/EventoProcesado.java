package mx.mexibanco.notificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "eventos_procesados")
public class EventoProcesado {

	@Id
	@Column(name = "evento_id")
	private UUID eventoId;

	@Column(name = "tipo")
	private String tipo;

	@Column(name = "transferencia_id")
	private Long transferenciaId;

	protected EventoProcesado() {
	}
}