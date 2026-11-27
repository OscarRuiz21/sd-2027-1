package mx.mexibanco.notificacion.mensajeria;

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
 * LAB S09, Parte 3: los eventos que notificacion ya proceso. eventoId es la LLAVE PRIMARIA (UNIQUE):
 * se inserta en la misma transaccion que los avisos. Si el mismo evento llega dos veces, la segunda
 * vez ya esta aqui y no se guarda nada; si llegan dos copias a la vez, la llave primaria deja pasar
 * a una y la transaccion de la otra se deshace (con sus avisos).
 */
@Entity
@Table(name = "evento_procesado")
public class EventoProcesado implements Persistable<String> {

	@Id
	@Column(length = 64)
	private String eventoId;

	@Column(nullable = false, length = 40)
	private String tipo;

	private Long transferenciaId;

	@Column(nullable = false)
	private Instant procesado;

	/** Siempre INSERT (nunca merge): un duplicado debe chocar con la llave primaria, no sobrescribirla. */
	@Transient
	private boolean nuevo = true;

	protected EventoProcesado() {
	}

	public EventoProcesado(EventoTransferencia evento) {
		this.eventoId = evento.eventoId();
		this.tipo = evento.tipo();
		this.transferenciaId = evento.transferenciaId();
		this.procesado = Instant.now();
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
