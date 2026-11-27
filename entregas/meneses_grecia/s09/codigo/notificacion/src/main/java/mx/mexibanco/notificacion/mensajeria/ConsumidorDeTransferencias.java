package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import mx.mexibanco.notificacion.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * LAB S09: el consumidor de los eventos de transferencia. Lee la cola notificacion.transferencias
 * (ver ConfiguracionMensajeria) con ACK MANUAL (spring.rabbitmq.listener.simple.acknowledge-mode=manual).
 *
 * Por cada mensaje:
 *  1. Lo lee como EventoTransferencia. Si no se puede leer (no es JSON, faltan campos, tipo
 *     desconocido) es un mensaje envenenado: basicNack SIN reencolar -> RabbitMQ lo manda al DLX y
 *     termina en notificacion.transferencias.dlq. La cola sigue fluyendo.
 *  2. Guarda los avisos (NotificacionService.registrarAvisos), en una transaccion que tambien guarda
 *     el eventoId. Si el evento ya se habia procesado (duplicado), no guarda nada.
 *  3. SOLO DESPUES de que la transaccion confirmo, basicAck. Si el proceso muere entre 2 y 3, el
 *     mensaje vuelve (redelivered=true) y el paso 2 lo reconoce como duplicado: nada se pierde y
 *     nada se repite.
 *  Si falla algo pasajero (la base caida), basicNack CON reencolar: se reintenta.
 *
 * Interruptores para provocar los problemas del lab (docker-compose.yml):
 *  - CONSUMIDOR_IDEMPOTENTE=false: no revisa el eventoId (Parte 3, "antes": avisos repetidos).
 *  - PAUSA_ANTES_DE_ACK_MS=20000: espera entre guardar y hacer ack, para apagar notificacion en medio.
 *  - REENCOLAR_ILEGIBLES=true: el mensaje envenenado se reencola (Parte 4, "antes": rebota sin fin).
 */
@Component
public class ConsumidorDeTransferencias {

	private static final Logger log = LoggerFactory.getLogger(ConsumidorDeTransferencias.class);

	private final NotificacionService notificaciones;
	private final ObjectMapper json;
	private final boolean idempotente;
	private final long pausaAntesDeAckMs;
	private final boolean reencolarIlegibles;

	public ConsumidorDeTransferencias(NotificacionService notificaciones, ObjectMapper json,
									  @Value("${mexibanco.consumidor.idempotente:true}") boolean idempotente,
									  @Value("${mexibanco.consumidor.pausa-antes-de-ack-ms:0}") long pausaAntesDeAckMs,
									  @Value("${mexibanco.consumidor.reencolar-ilegibles:false}") boolean reencolarIlegibles) {
		this.notificaciones = notificaciones;
		this.json = json;
		this.idempotente = idempotente;
		this.pausaAntesDeAckMs = pausaAntesDeAckMs;
		this.reencolarIlegibles = reencolarIlegibles;
		log.info("Consumidor de transferencias: idempotente={}, pausaAntesDeAckMs={}, reencolarIlegibles={}",
			idempotente, pausaAntesDeAckMs, reencolarIlegibles);
	}

	@RabbitListener(queues = ConfiguracionMensajeria.COLA)
	public void recibir(Message mensaje, Channel canal) throws IOException {
		MessageProperties propiedades = mensaje.getMessageProperties();
		long etiqueta = propiedades.getDeliveryTag();

		// 1. Leer. Un mensaje ilegible nunca se va a poder procesar: no tiene caso reintentarlo.
		EventoTransferencia evento;
		try {
			evento = leer(mensaje.getBody());
		} catch (EventoIlegibleException veneno) {
			log.error("Mensaje ILEGIBLE en {} (routing key {}, redelivered={}): {}. Cuerpo: {}",
				ConfiguracionMensajeria.COLA, propiedades.getReceivedRoutingKey(), propiedades.isRedelivered(),
				veneno.getMessage(), recortar(mensaje.getBody()));
			canal.basicNack(etiqueta, false, reencolarIlegibles);
			log.error(reencolarIlegibles
				? "Mensaje ilegible REENCOLADO: va a volver a llegar una y otra vez (REENCOLAR_ILEGIBLES=true)"
				: "Mensaje ilegible rechazado sin reencolar: RabbitMQ lo manda a " + ConfiguracionMensajeria.DLQ);
			return;
		}
		log.info("Evento {} {} de la transferencia {} recibido (redelivered={})",
			evento.tipo(), evento.eventoId(), evento.transferenciaId(), propiedades.isRedelivered());

		// 2. Guardar los avisos (y el eventoId) en una transaccion.
		try {
			int avisos = notificaciones.registrarAvisos(evento, idempotente);
			if (avisos == 0) {
				log.warn("Evento {} DUPLICADO: ya se habia procesado, no se guarda nada y se confirma", evento.eventoId());
			}
		} catch (DataIntegrityViolationException copiaSimultanea) {
			// Otra copia del mismo evento se guardo al mismo tiempo: la llave primaria la detuvo.
			log.warn("Evento {} DUPLICADO (llego otra copia a la vez): se confirma sin guardar", evento.eventoId());
		} catch (RuntimeException pasajera) {
			log.error("No se pudo guardar el evento {} ({}); se reencola para reintentar", evento.eventoId(), pasajera.getMessage());
			esperar(1000); // para no girar en caliente si la base esta caida
			canal.basicNack(etiqueta, false, true);
			return;
		}

		// 3. Ack DESPUES de guardar.
		if (pausaAntesDeAckMs > 0) {
			log.warn("PAUSA de {} ms entre guardar y hacer ack del evento {} (apaga notificacion ahora)",
				pausaAntesDeAckMs, evento.eventoId());
			esperar(pausaAntesDeAckMs);
		}
		canal.basicAck(etiqueta, false);
		log.info("Evento {} confirmado (ack)", evento.eventoId());
	}

	EventoTransferencia leer(byte[] cuerpo) {
		EventoTransferencia evento;
		try {
			evento = json.readValue(cuerpo, EventoTransferencia.class);
		} catch (IOException noEsJson) {
			String motivo = noEsJson instanceof JsonProcessingException jackson ? jackson.getOriginalMessage() : noEsJson.getMessage();
			throw new EventoIlegibleException("No es un EventoTransferencia en JSON: " + motivo, noEsJson);
		}
		if (evento == null) {
			throw new EventoIlegibleException("Mensaje vacio");
		}
		evento.validar();
		return evento;
	}

	private static String recortar(byte[] cuerpo) {
		String texto = new String(cuerpo, StandardCharsets.UTF_8);
		return texto.length() <= 300 ? texto : texto.substring(0, 300) + "...";
	}

	private static void esperar(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException interrumpido) {
			Thread.currentThread().interrupt();
		}
	}
}
