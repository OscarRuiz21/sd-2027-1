package mx.mexibanco.transferencia.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LAB S09, el lado del PRODUCTOR. Transferencia solo declara el exchange: no sabe ni le importa
 * quien escucha (la cola es de notificacion y la declara notificacion).
 *
 *  - Exchange "transferencias", tipo topic, durable (sobrevive a un reinicio del broker).
 *  - RabbitTemplate con Jackson2JsonMessageConverter: el evento sale como JSON. Se le pasa el
 *    ObjectMapper de Spring Boot para que la fecha salga como "2026-10-17T10:42:07Z" y no como numero.
 *  - mandatory=true: si el mensaje no llega a ninguna cola, el broker lo DEVUELVE (return) en vez de
 *    tirarlo en silencio. Junto con los publisher confirms (application.yml) es la evidencia de la
 *    Parte 1: "confirmado, pero sin cola que lo reciba".
 */
@Configuration
public class ConfiguracionMensajeria {

	public static final String EXCHANGE = "transferencias";
	public static final String CLAVE_REALIZADA = "transferencia.realizada";
	public static final String CLAVE_REVERTIDA = "transferencia.revertida";

	private static final Logger log = LoggerFactory.getLogger(ConfiguracionMensajeria.class);

	@Bean
	TopicExchange exchangeTransferencias() {
		return new TopicExchange(EXCHANGE, true, false);
	}

	@Bean
	Jackson2JsonMessageConverter convertidorJson(ObjectMapper objectMapper) {
		return new Jackson2JsonMessageConverter(objectMapper);
	}

	@Bean
	RabbitTemplate rabbitTemplate(RabbitTemplateConfigurer configurador, ConnectionFactory conexion,
								  Jackson2JsonMessageConverter convertidorJson) {
		RabbitTemplate plantilla = new RabbitTemplate();
		configurador.configure(plantilla, conexion); // aplica spring.rabbitmq.template.* del application.yml
		plantilla.setMessageConverter(convertidorJson);
		plantilla.setMandatory(true);
		plantilla.setReturnsCallback(devuelto -> log.warn(
			"RabbitMQ DEVOLVIO el evento {} (routing key {}): {} {}. Ninguna cola esta enlazada, el mensaje se pierde.",
			devuelto.getMessage().getMessageProperties().getMessageId(), devuelto.getRoutingKey(),
			devuelto.getReplyCode(), devuelto.getReplyText()));
		return plantilla;
	}

	/**
	 * El admin de Spring declara el exchange la primera vez que se abre una conexion. Esto lo declara al
	 * arrancar para que aparezca en el panel desde el inicio. Si RabbitMQ no esta, no pasa nada: se
	 * declarara con la primera publicacion.
	 */
	@Bean
	ApplicationRunner declararAlArrancar(AmqpAdmin admin, TopicExchange exchangeTransferencias) {
		return argumentos -> {
			try {
				admin.declareExchange(exchangeTransferencias);
				log.info("Exchange '{}' declarado en RabbitMQ", EXCHANGE);
			} catch (RuntimeException brokerCaido) {
				log.warn("RabbitMQ no disponible al arrancar ({}); el exchange se declarara despues", brokerCaido.getMessage());
			}
		};
	}
}
