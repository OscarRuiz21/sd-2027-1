package mx.mexibanco.notificacion.mensajeria;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {

	@Id
	private String eventoId;

	private Instant procesadoEn;

	protected EventoProcesado() {}

	public EventoProcesado(String eventoId) {
		this.eventoId = eventoId;
		this.procesadoEn = Instant.now();
	}

	public String getEventoId() {
		return eventoId;
	}

	public Instant getProcesadoEn() {
		return procesadoEn;
	}
}