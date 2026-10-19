package mx.mexibanco.mensajeria.outbox;

import jakarta.persistence.*;
import mx.mexibanco.mensajeria.EventoTransferencia;

import java.time.Instant;
import java.util.UUID;

/**
 * Reto S09 (Transactional Outbox): un evento que la saga YA decidio publicar, guardado en la base de
 * transferencia en la MISMA transaccion que el cambio de estado final. Mientras publicado sea null,
 * el RelayDeEventos lo sigue intentando mandar al broker.
 */
@Entity
@Table(name = "outbox")
public class EventoSaliente {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private UUID eventoId;

	@Column(nullable = false, length = 40)
	private String tipo;

	@Column
	private Long transferenciaId;

	/** El evento tal como va a viajar, en JSON. */
	@Column(nullable = false, columnDefinition = "text")
	private String payload;

	@Column(nullable = false)
	private Instant creado;

	/** Cuando el broker lo confirmo. Null = pendiente. */
	@Column
	private Instant publicado;

	@Column(nullable = false)
	private int intentos;

	@Column(length = 500)
	private String ultimoError;

	protected EventoSaliente() {
	}

	public EventoSaliente(EventoTransferencia evento, String payload) {
		this.eventoId = evento.eventoId();
		this.tipo = evento.tipo();
		this.transferenciaId = evento.transferenciaId();
		this.payload = payload;
		this.creado = Instant.now();
	}

	public void marcarPublicado() {
		this.intentos++;
		this.publicado = Instant.now();
		this.ultimoError = null;
	}

	public void registrarFalla(String error) {
		this.intentos++;
		this.ultimoError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
	}

	public Long getId() {
		return id;
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

	public Instant getPublicado() {
		return publicado;
	}

	public int getIntentos() {
		return intentos;
	}
}
