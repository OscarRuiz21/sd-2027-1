package mx.mexibanco.transferencia.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.mexibanco.transferencia.Transferencia;
import mx.mexibanco.transferencia.TransferenciaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * LAB S09: guarda el estado final de la saga Y deja salir su evento. Dos modos (EVENTOS_MODO):
 *
 *  - "directo" (Parte 1): guarda la fila y DESPUES publica en RabbitMQ. Son dos escrituras
 *    separadas: si RabbitMQ esta caido, la base dice COMPLETADA y el evento se pierde (queda en el log).
 *    La transferencia no falla por eso: el aviso ya no tumba la operacion.
 *
 *  - "outbox" (reto, el modo por omision): en UNA transaccion local guarda la fila y una fila en la
 *    tabla outbox con el evento. Nada sale al broker aqui; RelayDeOutbox lo publica en los
 *    siguientes segundos y, si RabbitMQ esta caido, lo reintenta hasta que vuelva.
 */
@Component
public class SalidaDeEventos {

	private static final Logger log = LoggerFactory.getLogger(SalidaDeEventos.class);

	private final TransferenciaRepository transferencias;
	private final OutboxRepository outbox;
	private final PublicadorDeEventos publicador;
	private final ObjectMapper json;
	private final TransactionTemplate transaccion;
	private final boolean usarOutbox;

	public SalidaDeEventos(TransferenciaRepository transferencias, OutboxRepository outbox, PublicadorDeEventos publicador,
						   ObjectMapper json, PlatformTransactionManager transacciones,
						   @Value("${mexibanco.eventos.modo:outbox}") String modo) {
		this.transferencias = transferencias;
		this.outbox = outbox;
		this.publicador = publicador;
		this.json = json;
		this.transaccion = new TransactionTemplate(transacciones);
		this.usarOutbox = !"directo".equalsIgnoreCase(modo.trim());
		log.info("Eventos de transferencia en modo {}", usarOutbox ? "OUTBOX" : "DIRECTO");
	}

	public Transferencia guardarYAvisar(Transferencia t, EventoTransferencia evento) {
		return usarOutbox ? guardarConOutbox(t, evento) : guardarYPublicar(t, evento);
	}

	private Transferencia guardarConOutbox(Transferencia t, EventoTransferencia evento) {
		String payload = aJson(evento);
		Transferencia guardada = transaccion.execute(estado -> {
			Transferencia fila = transferencias.save(t);
			outbox.save(new EventoPendiente(evento, payload));
			return fila;
		});
		log.info("Transferencia {}: {} (evento {} {} en el outbox)", guardada.getId(), guardada.getEstado(),
			evento.tipo(), evento.eventoId());
		return guardada;
	}

	private Transferencia guardarYPublicar(Transferencia t, EventoTransferencia evento) {
		Transferencia guardada = transferencias.save(t);
		log.info("Transferencia {}: {}", guardada.getId(), guardada.getEstado());
		try {
			publicador.publicar(evento);
		} catch (EventoNoPublicadoException perdido) {
			// La doble escritura del reto: la base ya dice COMPLETADA/COMPENSADA y el evento no salio.
			log.error("EVENTO PERDIDO: {} {} de la transferencia {} no llego a RabbitMQ ({}). Nadie le avisara al cliente.",
				evento.tipo(), evento.eventoId(), evento.transferenciaId(), perdido.getMessage());
		}
		return guardada;
	}

	private String aJson(EventoTransferencia evento) {
		try {
			return json.writeValueAsString(evento);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("No se pudo convertir el evento a JSON", e);
		}
	}
}
