package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import mx.mexibanco.notificacion.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.Header;

import java.io.IOException;

@Configuration
public class ConsumidorDeTransferencias {

	private static final Logger log = LoggerFactory.getLogger(ConsumidorDeTransferencias.class);

	public static final String EXCHANGE_TRANSFERENCIAS = "transferencias";
	public static final String COLA_NOTIFICACIONES = "notificacion.transferencias";
	public static final String ROUTING_KEY_PATRON = "transferencia.*";

	public static final String EXCHANGE_DLX = "transferencias.dlx";
	public static final String COLA_DLQ = "notificacion.transferencias.dlq";
	public static final String ROUTING_KEY_DLQ = "notificacion.transferencias.muerta";

	private final NotificacionService notificacionService;
	private final ObjectMapper objectMapper;

	public ConsumidorDeTransferencias(NotificacionService notificacionService, ObjectMapper objectMapper) {
		this.notificacionService = notificacionService;
		this.objectMapper = objectMapper;
	}

	@Bean
	public TopicExchange exchangeTransferencias() {
		return new TopicExchange(EXCHANGE_TRANSFERENCIAS, true, false);
	}

	@Bean
	public DirectExchange deadLetterExchange() {
		return new DirectExchange(EXCHANGE_DLX, true, false);
	}

	@Bean
	public Queue deadLetterQueue() {
		return QueueBuilder.durable(COLA_DLQ).build();
	}

	@Bean
	public Binding bindingDlq(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
		return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(ROUTING_KEY_DLQ);
	}

	@Bean
	public Queue colaNotificaciones() {
		return QueueBuilder.durable(COLA_NOTIFICACIONES)
				.withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
				.withArgument("x-dead-letter-routing-key", ROUTING_KEY_DLQ)
				.build();
	}

	@Bean
	public Binding bindingNotificaciones(Queue colaNotificaciones, TopicExchange exchangeTransferencias) {
		return BindingBuilder.bind(colaNotificaciones).to(exchangeTransferencias).with(ROUTING_KEY_PATRON);
	}

	@RabbitListener(queues = COLA_NOTIFICACIONES)
	public void recibirEvento(Message message, Channel channel,
							  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
		try {
			EventoTransferencia evento = objectMapper.readValue(message.getBody(), EventoTransferencia.class);
			notificacionService.procesarEventoTransferencia(evento);
			channel.basicAck(deliveryTag, false);
		} catch (Exception e) {
			log.error("[notificacion] mensaje invalido enviado a DLQ: {}", e.getMessage());
			channel.basicNack(deliveryTag, false, false);
		}
	}
}