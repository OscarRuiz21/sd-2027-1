package mx.mexibanco.mensajeria;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Lab S09, reto: una fila de la tabla outbox, es decir, un evento que la saga YA decidio publicar y que
 * todavia puede no haber salido al broker. Se escribe en la misma transaccion local que el estado
 * final de la transferencia (ver TransferenciaService.guardarConEvento), y RelayDelOutbox la publica
 * despues. publicado en null = pendiente.
 */
@Entity
@Table(name = "outbox")
public class EventoPendiente {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** El mismo eventoId del evento: si el relay lo publica dos veces, sale con el mismo id. */
	@Column(nullable = false, unique = true)
	private UUID eventoId;

	@Column(nullable = false, length = 40)
	private String tipo;

	@Column(nullable = false)
	private Long transferenciaId;

	/** El evento tal como va a salir, en JSON. */
	@Column(nullable = false, columnDefinition = "text")
	private String carga;

	@Column(nullable = false)
	private Instant creado;

	@Column
	private Instant publicado;

	@Column(nullable = false)
	private int intentos;

	@Column(length = 500)
	private String ultimoError;

	protected EventoPendiente() {
	}

	public EventoPendiente(EventoTransferencia evento, String carga) {
		this.eventoId = evento.eventoId();
		this.tipo = evento.tipo();
		this.transferenciaId = evento.transferenciaId();
		this.carga = carga;
		this.creado = Instant.now();
	}

	public void marcarPublicado() {
		this.intentos++;
		this.publicado = Instant.now();
		this.ultimoError = null;
	}

	public void anotarFalla(String error) {
		this.intentos++;
		this.ultimoError = error == null || error.length() <= 500 ? error : error.substring(0, 500);
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

	public String getCarga() {
		return carga;
	}

	public Instant getPublicado() {
		return publicado;
	}

	public int getIntentos() {
		return intentos;
	}
}
