package mx.mexibanco.transferencia.mensajeria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * LAB S09, reto: una fila de la tabla OUTBOX. Se escribe en la MISMA transaccion local que el
 * cambio de estado final de la transferencia (COMPLETADA o COMPENSADA): o quedan los dos o ninguno.
 * Asi ya no hay "doble escritura" (base + broker) que pueda quedar a medias. RelayDeOutbox la
 * publica despues y le pone fecha en "publicado".
 *
 * La llave primaria es el eventoId: el mismo evento no puede quedar dos veces en el outbox.
 */
@Entity
@Table(name = "outbox")
public class EventoPendiente implements Persistable<String> {

	@Id
	@Column(length = 36)
	private String eventoId;

	@Column(nullable = false, length = 40)
	private String tipo;

	@Column(nullable = false)
	private Long transferenciaId;

	/** El evento completo en JSON, tal como saldra al broker. */
	@Column(nullable = false, columnDefinition = "text")
	private String payload;

	@Column(nullable = false)
	private Instant creado;

	/** Null mientras no se publique. */
	private Instant publicado;

	private int intentos;

	@Column(length = 500)
	private String ultimoError;

	/** Para que save() haga INSERT y no un merge (la llave la ponemos nosotros, no la base). */
	@Transient
	private boolean nuevo = true;

	protected EventoPendiente() {
	}

	public EventoPendiente(EventoTransferencia evento, String payload) {
		this.eventoId = evento.eventoId();
		this.tipo = evento.tipo();
		this.transferenciaId = evento.transferenciaId();
		this.payload = payload;
		this.creado = Instant.now();
	}

	public void marcarPublicado() {
		this.publicado = Instant.now();
		this.intentos++;
		this.ultimoError = null;
	}

	public void registrarFallo(String error) {
		this.intentos++;
		this.ultimoError = error == null || error.length() <= 500 ? error : error.substring(0, 497) + "...";
	}

	@PostLoad
	@PostPersist
	void yaExiste() {
		this.nuevo = false;
	}

	@Override
	public String getId() {
		return eventoId;
	}

	@Override
	public boolean isNew() {
		return nuevo;
	}

	public String getEventoId() {
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

	public String getUltimoError() {
		return ultimoError;
	}
}
