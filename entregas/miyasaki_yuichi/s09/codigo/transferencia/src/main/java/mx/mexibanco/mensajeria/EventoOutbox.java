package mx.mexibanco.mensajeria;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Reto S09 (transactional outbox): un evento que la saga ya decidio mandar, guardado en la base de
 * transferencia en la MISMA transaccion que el estado final (COMPLETADA o COMPENSADA). Si esa
 * transaccion se confirma, el evento existe; si se deshace, tampoco. Ya no hay doble escritura: la saga
 * escribe en un solo sistema, y RelayDeOutbox lo publica despues y le pone fecha a publicado.
 */
@Entity
@Table(name = "outbox")
public class EventoOutbox {

	@Id
	private UUID eventoId;

	@Column(nullable = false, length = 40)
	private String tipo;

	@Column(nullable = false)
	private Long transferenciaId;

	/** El evento tal como sale al broker: JSON con el ObjectMapper de Spring Boot. */
	@Column(nullable = false, length = 2000)
	private String payload;

	@Column(nullable = false)
	private Instant creado;

	/** Null mientras esta pendiente. */
	@Column
	private Instant publicado;

	/** Cuantas veces el relay intento publicarlo sin que el broker lo confirmara. */
	@Column(nullable = false)
	private int intentos;

	protected EventoOutbox() {
	}

	public EventoOutbox(EventoTransferencia evento, String payload) {
		this.eventoId = evento.eventoId();
		this.tipo = evento.tipo();
		this.transferenciaId = evento.transferenciaId();
		this.payload = payload;
		this.creado = Instant.now();
		this.intentos = 0;
	}

	public void marcarPublicado() {
		this.publicado = Instant.now();
	}

	public void contarIntento() {
		this.intentos++;
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

	public String getPayload() {
		return payload;
	}

	public Instant getCreado() {
		return creado;
	}

	public Instant getPublicado() {
		return publicado;
	}

	public int getIntentos() {
		return intentos;
	}
}
