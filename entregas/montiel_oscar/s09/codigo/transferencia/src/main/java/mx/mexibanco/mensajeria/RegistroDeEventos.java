package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.mexibanco.transferencia.Transferencia;
import mx.mexibanco.transferencia.TransferenciaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Persiste el estado final de la saga y su evento en una sola transaccion local. */
@Component
public class RegistroDeEventos {
	private final TransferenciaRepository transferencias;
	private final EventoOutboxRepository outbox;
	private final ObjectMapper json;
	private final TransactionTemplate transacciones;

	public RegistroDeEventos(TransferenciaRepository transferencias, EventoOutboxRepository outbox,
			ObjectMapper json, TransactionTemplate transacciones) {
		this.transferencias = transferencias;
		this.outbox = outbox;
		this.json = json;
		this.transacciones = transacciones;
	}

	public Transferencia guardarConEvento(Transferencia transferencia, EventoTransferencia evento) {
		String contenido;
		try {
			contenido = json.writeValueAsString(evento);
		} catch (JsonProcessingException error) {
			throw new IllegalStateException("No se pudo serializar el evento " + evento.eventoId(), error);
		}
		return transacciones.execute(estado -> {
			Transferencia guardada = transferencias.save(transferencia);
			outbox.save(new EventoOutbox(evento.eventoId(), contenido));
			return guardada;
		});
	}
}
