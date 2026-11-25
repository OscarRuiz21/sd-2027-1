package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.rabbitmq.client.Channel;
import mx.mexibanco.notificacion.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

import java.io.IOException;

@Component
class ConsumidorDeTransferencias {

	private static final Logger LOG = LoggerFactory.getLogger(ConsumidorDeTransferencias.class);
	private final ObjectReader lector;
	private final NotificacionService avisos;

	ConsumidorDeTransferencias(ObjectMapper mapper, NotificacionService avisos) {
		this.lector = mapper.readerFor(EventoTransferencia.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
		this.avisos = avisos;
	}

	@RabbitListener(queues = ColasTransferencias.COLA, ackMode = "MANUAL")
	public void consumir(Message mensaje, Channel canal) throws IOException {
		long entrega = mensaje.getMessageProperties().getDeliveryTag();
		EventoTransferencia evento;
		try {
			evento = lector.readValue(mensaje.getBody());
		} catch (IOException error) {
			rechazar(canal, entrega, "JSON inválido");
			return;
		}
		if (evento == null || !evento.validoPara(mensaje.getMessageProperties().getReceivedRoutingKey())) {
			rechazar(canal, entrega, "El evento no cumple el contrato");
			return;
		}

		boolean nuevo;
		try {
			nuevo = avisos.registrarEvento(evento);
		} catch (DataAccessException | TransactionException error) {
			LOG.warn("Aviso pendiente por un fallo de base de datos: {}", evento.eventoId());
			try {
				Thread.sleep(1000);
			} catch (InterruptedException interrupcion) {
				Thread.currentThread().interrupt();
			}
			canal.basicNack(entrega, false, true);
			return;
		}
		canal.basicAck(entrega, false);
		if (nuevo) {
			LOG.info("Evento {} guardado | transferencia #{} | {}", evento.eventoId(), evento.transferenciaId(), evento.tipo());
		} else {
			LOG.info("Evento {} duplicado; se confirmó sin crear avisos", evento.eventoId());
		}
	}

	private void rechazar(Channel canal, long entrega, String motivo) throws IOException {
		LOG.warn("{}; se envía el mensaje a DLQ", motivo);
		canal.basicNack(entrega, false, false);
	}
}
