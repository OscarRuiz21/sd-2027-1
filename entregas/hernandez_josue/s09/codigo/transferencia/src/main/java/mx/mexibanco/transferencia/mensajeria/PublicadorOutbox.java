package mx.mexibanco.transferencia.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
class PublicadorOutbox {

	private static final Logger LOG = LoggerFactory.getLogger(PublicadorOutbox.class);
	private final JdbcTemplate jdbc;
	private final RabbitTemplate rabbit;
	private final ObjectMapper mapper;

	private record Pendiente(UUID eventoId, String clave, String cuerpo) {}

	PublicadorOutbox(JdbcTemplate jdbc, RabbitTemplate rabbit, ObjectMapper mapper) {
		this.jdbc = jdbc;
		this.rabbit = rabbit;
		this.mapper = mapper;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean enviarSiguiente() throws Exception {
		var pendientes = jdbc.query("""
			SELECT evento_id, clave, cuerpo FROM outbox
			WHERE publicada IS NULL
			ORDER BY creada, evento_id
			LIMIT 1 FOR UPDATE SKIP LOCKED
			""", (fila, numero) -> new Pendiente(fila.getObject("evento_id", UUID.class),
				fila.getString("clave"), fila.getString("cuerpo")));
		if (pendientes.isEmpty()) {
			return false;
		}
		Pendiente fila = pendientes.getFirst();
		EventoTransferencia evento = mapper.readValue(fila.cuerpo(), EventoTransferencia.class);
		CorrelationData confirmacion = new CorrelationData(fila.eventoId().toString());
		rabbit.convertAndSend(RabbitTransferencias.EXCHANGE, fila.clave(), evento, mensaje -> {
			mensaje.getMessageProperties().setMessageId(confirmacion.getId());
			mensaje.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
			return mensaje;
		}, confirmacion);

		var respuesta = confirmacion.getFuture().get(3, TimeUnit.SECONDS);
		if (!respuesta.isAck()) {
			throw new IllegalStateException("RabbitMQ rechazó el evento: " + respuesta.getReason());
		}
		if (confirmacion.getReturned() != null) {
			throw new IllegalStateException("Evento sin ruta: " + confirmacion.getReturned().getReplyText());
		}
		jdbc.update("UPDATE outbox SET publicada = now() WHERE evento_id = ?", fila.eventoId());
		LOG.info("Evento confirmado {} | {}", fila.clave(), fila.eventoId());
		return true;
	}
}
