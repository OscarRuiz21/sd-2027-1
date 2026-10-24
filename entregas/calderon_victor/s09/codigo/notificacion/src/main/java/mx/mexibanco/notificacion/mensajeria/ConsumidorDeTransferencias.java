package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import mx.mexibanco.notificacion.EventoTransferencia;
import mx.mexibanco.notificacion.Notificacion;
import mx.mexibanco.notificacion.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lab S09: el consumidor de los eventos de transferencia. Lee cada mensaje de la cola
 * notificacion.transferencias, guarda los avisos y SOLO DESPUES hace ack (acknowledge-mode: manual
 * en application.yml).
 *
 * Por que en ese orden: cuando RabbitMQ entrega un mensaje lo deja Unacked y espera la respuesta.
 * Si el proceso muere despues del commit y antes del ack, el broker no recibio el ack y vuelve a
 * entregar el mensaje (redelivered=true): no se pierde nada, pero puede llegar dos veces. Por eso
 * NotificacionService.avisarDeTransferencia es idempotente (Parte 3).
 *
 * Cada mensaje termina de una de tres formas (Parte 4):
 *  - ack: se proceso (o ya se habia procesado antes, y se reconocio el duplicado);
 *  - nack SIN reencolar: no es un evento valido. Reintentarlo no lo arregla, asi que el broker lo
 *    manda a la DLQ (notificacion.transferencias.dlq) para que lo revise una persona;
 *  - nack reencolando: el mensaje esta bien pero fallo algo nuestro (la base, por ejemplo). Ese si
 *    vale la pena intentarlo otra vez.
 */
@Component
public class ConsumidorDeTransferencias {

	private static final Logger log = LoggerFactory.getLogger(ConsumidorDeTransferencias.class);

	private final NotificacionService notificaciones;
	private final ObjectMapper json;
	private final long pausaAntesDelAckMs;
	private final long esperaAntesDeReencolarMs;

	public ConsumidorDeTransferencias(NotificacionService notificaciones, ObjectMapper json,
									  @Value("${mexibanco.demo.pausa-antes-del-ack-ms:0}") long pausaAntesDelAckMs,
									  @Value("${mexibanco.mensajeria.espera-antes-de-reencolar-ms:2000}") long esperaAntesDeReencolarMs) {
		this.notificaciones = notificaciones;
		this.json = json;
		this.pausaAntesDelAckMs = pausaAntesDelAckMs;
		this.esperaAntesDeReencolarMs = esperaAntesDeReencolarMs;
	}

	@RabbitListener(queues = ConfiguracionMensajeria.COLA)
	public void recibir(Message mensaje, Channel canal) throws IOException {
		long etiqueta = mensaje.getMessageProperties().getDeliveryTag();
		boolean reentregado = Boolean.TRUE.equals(mensaje.getMessageProperties().getRedelivered());

		EventoTransferencia evento;
		try {
			evento = leer(mensaje.getBody());
		} catch (EventoIlegibleException veneno) {
			log.error("[consumidor] mensaje envenenado (redelivered={}): {}. Se rechaza sin reencolar y el broker lo manda a {}",
				reentregado, veneno.getMessage(), ConfiguracionMensajeria.DLQ);
			canal.basicNack(etiqueta, false, false); // el ultimo false es requeue: no lo regreses a la cola
			return;
		}

		List<Notificacion> avisos;
		try {
			avisos = notificaciones.avisarDeTransferencia(evento); // aqui hace commit
		} catch (RuntimeException fallaNuestra) {
			log.warn("[consumidor] no se pudieron guardar los avisos del evento {} ({}); se regresa a la cola en {} ms",
				evento.eventoId(), fallaNuestra.getMessage(), esperaAntesDeReencolarMs);
			// La pausa es para no darle vueltas en caliente al mismo mensaje mientras la base no vuelve.
			esperar(esperaAntesDeReencolarMs);
			canal.basicNack(etiqueta, false, true);
			return;
		}
		log.info("[consumidor] {} de la transferencia {} (evento {}, redelivered={}): {} aviso(s) nuevo(s)",
			evento.tipo(), evento.transferenciaId(), evento.eventoId(), reentregado, avisos.size());

		pausaDeDemo(evento);
		canal.basicAck(etiqueta, false); // DESPUES de guardar: "ya lo tengo, borralo"
	}

	/**
	 * El JSON se lee a mano (y no con un convertidor) para que un mensaje ilegible sea un caso mas de
	 * este metodo. Cuenta como veneno no solo el JSON roto: tambien un JSON bien formado al que le
	 * falta lo que el contrato pide (sin eventoId no hay idempotencia posible) o con un tipo que no
	 * existe. Ninguno de esos se arregla por mas que se reintente.
	 */
	EventoTransferencia leer(byte[] cuerpo) throws EventoIlegibleException {
		EventoTransferencia evento;
		try {
			evento = json.readValue(cuerpo, EventoTransferencia.class);
		} catch (IOException noEsUnEvento) {
			String causa = noEsUnEvento instanceof JsonProcessingException jackson ? jackson.getOriginalMessage() : noEsUnEvento.getMessage();
			throw new EventoIlegibleException("no es un evento en JSON: " + causa, noEsUnEvento);
		}
		if (evento == null) {
			throw new EventoIlegibleException("el cuerpo viene vacio");
		}
		if (!EventoTransferencia.REALIZADA.equals(evento.tipo()) && !EventoTransferencia.REVERTIDA.equals(evento.tipo())) {
			throw new EventoIlegibleException("tipo de evento desconocido: " + evento.tipo());
		}
		List<String> faltan = new ArrayList<>();
		if (evento.eventoId() == null) faltan.add("eventoId");
		if (evento.transferenciaId() == null) faltan.add("transferenciaId");
		if (evento.clabeOrigen() == null) faltan.add("clabeOrigen");
		if (evento.monto() == null) faltan.add("monto");
		if (evento.saldoOrigen() == null) faltan.add("saldoOrigen");
		if (EventoTransferencia.REALIZADA.equals(evento.tipo())) {
			if (evento.clabeDestino() == null) faltan.add("clabeDestino");
			if (evento.saldoDestino() == null) faltan.add("saldoDestino");
		}
		if (!faltan.isEmpty()) {
			throw new EventoIlegibleException("al evento " + evento.tipo() + " le falta " + String.join(", ", faltan));
		}
		return evento;
	}

	/**
	 * Solo para la Parte 3 del lab: una ventana entre el commit y el ack para matar el contenedor en
	 * medio y ver la reentrega. Con PAUSA_ANTES_DEL_ACK_MS=0 (lo normal) no hace nada.
	 */
	private void pausaDeDemo(EventoTransferencia evento) {
		if (pausaAntesDelAckMs <= 0) {
			return;
		}
		log.info("[consumidor] avisos guardados; espero {} ms antes del ack del evento {}", pausaAntesDelAckMs, evento.eventoId());
		esperar(pausaAntesDelAckMs);
	}

	private static void esperar(long ms) {
		if (ms <= 0) {
			return;
		}
		try {
			Thread.sleep(ms);
		} catch (InterruptedException interrumpido) {
			Thread.currentThread().interrupt();
		}
	}
}
