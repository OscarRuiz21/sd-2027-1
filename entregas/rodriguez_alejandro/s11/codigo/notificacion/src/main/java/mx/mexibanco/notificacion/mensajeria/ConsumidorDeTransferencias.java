package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import mx.mexibanco.notificacion.EventoTransferencia;
import mx.mexibanco.notificacion.NotificacionService;
import mx.mexibanco.notificacion.avisos.BitacoraDeAvisos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ConsumidorDeTransferencias {

	private static final Logger log = LoggerFactory.getLogger(ConsumidorDeTransferencias.class);

	private final NotificacionService notificaciones;
	private final BitacoraDeAvisos bitacora;
	private final ObjectMapper json;
	private final Validator validador;
	private final long pausaAntesDelAckMs;
	private final long esperaAntesDeReencolarMs;

	public ConsumidorDeTransferencias(NotificacionService notificaciones, BitacoraDeAvisos bitacora, ObjectMapper json, Validator validador,
									  @Value("${mexibanco.demo.pausa-antes-del-ack-ms:0}") long pausaAntesDelAckMs,
									  @Value("${mexibanco.mensajeria.espera-antes-de-reencolar-ms:1000}") long esperaAntesDeReencolarMs) {
		this.notificaciones = notificaciones;
		this.bitacora = bitacora;
		this.json = json;
		this.validador = validador;
		this.pausaAntesDelAckMs = pausaAntesDelAckMs;
		this.esperaAntesDeReencolarMs = esperaAntesDeReencolarMs;
	}

	@RabbitListener(queues = ConfiguracionMensajeria.COLA)
	public void recibir(Message mensaje, Channel canal) throws IOException {
		long etiqueta = mensaje.getMessageProperties().getDeliveryTag();
		boolean reentregado = Boolean.TRUE.equals(mensaje.getMessageProperties().getRedelivered());

		EventoTransferencia evento;
		try {
			evento = leer(mensaje);
		} catch (MensajeInvalido invalido) {
			log.warn("[consumidor] mensaje invalido, va a la DLQ: {}", invalido.getMessage());
			canal.basicNack(etiqueta, false, false);
			return;
		}

		int nuevos;
		try {
			// S11: Escribir los avisos en MongoDB directamente, NO a la bandeja de Postgres.
			nuevos = bitacora.registrar(evento);
		} catch (RuntimeException fallaNuestra) {
			log.error("[consumidor] no se pudo guardar el evento {}; se reencola: {}", evento.eventoId(), fallaNuestra.getMessage());
			pausa(esperaAntesDeReencolarMs);
			canal.basicNack(etiqueta, false, true);
			return;
		}
		log.info("[consumidor] {} de la transferencia {} (evento {}, redelivered={}): {} aviso(s) nuevo(s)",
			evento.tipo(), evento.transferenciaId(), evento.eventoId(), reentregado, nuevos);

		if (pausaAntesDelAckMs > 0) {
			log.info("[consumidor] avisos guardados; espero {} ms antes del ack", pausaAntesDelAckMs);
			pausa(pausaAntesDelAckMs);
		}
		canal.basicAck(etiqueta, false);
	}

	private EventoTransferencia leer(Message mensaje) {
		EventoTransferencia evento;
		try {
			evento = json.readValue(mensaje.getBody(), EventoTransferencia.class);
		} catch (IOException noEsElJsonEsperado) {
			String causa = String.valueOf(noEsElJsonEsperado.getMessage()).lines().findFirst().orElse("");
			throw new MensajeInvalido("no se pudo leer el JSON (" + causa + "): " + recorte(mensaje.getBody()));
		}
		if (evento == null) {
			throw new MensajeInvalido("cuerpo vacio");
		}
		Set<ConstraintViolation<EventoTransferencia>> problemas = validador.validate(evento);
		if (!problemas.isEmpty()) {
			throw new MensajeInvalido(problemas.stream()
				.map(p -> p.getPropertyPath() + " " + p.getMessage())
				.sorted()
				.collect(Collectors.joining(", ")) + ": " + recorte(mensaje.getBody()));
		}
		return evento;
	}

	private static String recorte(byte[] cuerpo) {
		String texto = new String(cuerpo, StandardCharsets.UTF_8);
		return texto.length() <= 200 ? texto : texto.substring(0, 200) + "...";
	}

	private static void pausa(long ms) {
		if (ms <= 0) {
			return;
		}
		try {
			Thread.sleep(ms);
		} catch (InterruptedException interrumpido) {
			Thread.currentThread().interrupt();
		}
	}

	private static class MensajeInvalido extends RuntimeException {
		MensajeInvalido(String detalle) {
			super(detalle);
		}
	}
}