package mx.mexibanco.mensajeria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "outbox_evento")
public class EventoOutbox {
	@Id
	private UUID eventoId;

	@Column(nullable = false, length = 2000)
	private String contenido;

	@Column(nullable = false)
	private Instant creadoEn;

	private Instant publicadoEn;

	protected EventoOutbox() { }

	public EventoOutbox(UUID eventoId, String contenido) {
		this.eventoId = eventoId;
		this.contenido = contenido;
		this.creadoEn = Instant.now();
	}

	public UUID getEventoId() { return eventoId; }
	public String getContenido() { return contenido; }
	public Instant getPublicadoEn() { return publicadoEn; }
	public void marcarPublicado() { this.publicadoEn = Instant.now(); }
}
