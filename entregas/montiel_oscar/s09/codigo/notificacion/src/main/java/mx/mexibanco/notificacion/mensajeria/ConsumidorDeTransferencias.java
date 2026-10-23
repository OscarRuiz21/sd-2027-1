package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import mx.mexibanco.notificacion.EventoTransferencia;
import mx.mexibanco.notificacion.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ConsumidorDeTransferencias {
	private static final Logger log = LoggerFactory.getLogger(ConsumidorDeTransferencias.class);
	private final NotificacionService notificaciones;
	private final ObjectMapper json;

	public ConsumidorDeTransferencias(NotificacionService notificaciones, ObjectMapper json) {
		this.notificaciones = notificaciones;
		this.json = json;
	}

	@RabbitListener(queues = ConfiguracionMensajeria.COLA)
	public void recibir(Message mensaje, Channel canal) throws IOException {
		long etiqueta = mensaje.getMessageProperties().getDeliveryTag();
		EventoTransferencia evento;
		try {
			evento = json.readValue(mensaje.getBody(), EventoTransferencia.class);
			evento.validar();
		} catch (JsonProcessingException | IllegalArgumentException invalido) {
			log.warn("[consumidor] Evento invalido; enviado a DLQ: {}", invalido.getMessage());
			canal.basicNack(etiqueta, false, false);
			return;
		}

		try {
			int nuevos = notificaciones.avisarDeTransferencia(evento).size();
			log.info("[consumidor] {} de la transferencia {} (evento {}, redelivered={}): {} aviso(s) nuevo(s)",
					evento.tipo(), evento.transferenciaId(), evento.eventoId(),
					mensaje.getMessageProperties().isRedelivered(), nuevos);
		} catch (RuntimeException fallo) {
			log.error("[consumidor] No se pudo guardar el evento {}; se reencola", evento.eventoId(), fallo);
			canal.basicNack(etiqueta, false, true);
			return;
		}
		canal.basicAck(etiqueta, false);
	}
}
