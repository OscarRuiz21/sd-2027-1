package mx.mexibanco.notificacion.mensajeria;

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
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * S09: el consumidor de los eventos de transferencia, con ACK MANUAL (acknowledge-mode: manual).
 *
 * Cuando RabbitMQ entrega un mensaje lo marca Unacked y espera la respuesta. Si respondemos basicAck,
 * lo borra. Si el proceso muere sin responder, lo vuelve a poner en la cola. El ack va DESPUES del
 * commit de los avisos: si notificacion muere entre guardar y el ack, el broker lo vuelve a entregar
 * con redelivered=true. No se pierde nada, pero puede llegar dos veces (at-least-once).
 *
 * El JSON se lee a mano (y no con el convertidor) para que un mensaje ilegible sea un caso mas de este
 * metodo. Tres desenlaces por mensaje:
 *  - ack: se guardaron los avisos (o ya se habian guardado: idempotente).
 *  - nack SIN reencolar: el mensaje es veneno (JSON ilegible o que no cumple el contrato). Reintentar
 *    no lo arregla; el broker lo manda a la DLQ por los argumentos de la cola.
 *  - nack REENCOLANDO: el mensaje esta bien y lo que fallo fue NUESTRO (la base, por ejemplo). Vale
 *    la pena reintentarlo.
 */
@Component
public class ConsumidorDeTransferencias {

	private static final Logger log = LoggerFactory.getLogger(ConsumidorDeTransferencias.class);

	private final NotificacionService notificaciones;
	private final ObjectMapper json;
	private final long pausaAntesDelAckMs;

	public ConsumidorDeTransferencias(NotificacionService notificaciones, ObjectMapper json,
									  @Value("${mexibanco.demo.pausa-antes-del-ack-ms:0}") long pausaAntesDelAckMs) {
		this.notificaciones = notificaciones;
		this.json = json;
		this.pausaAntesDelAckMs = pausaAntesDelAckMs;
	}

	@RabbitListener(queues = ConfiguracionMensajeria.COLA)
	public void recibir(Message mensaje, Channel canal) throws IOException {
		long etiqueta = mensaje.getMessageProperties().getDeliveryTag();
		boolean reentregado = Boolean.TRUE.equals(mensaje.getMessageProperties().getRedelivered());

		EventoTransferencia evento;
		try {
			evento = json.readValue(mensaje.getBody(), EventoTransferencia.class);   // JSON ilegible: IOException
			if (evento == null) {                                                 // el cuerpo era "null"
				throw new IllegalArgumentException("el cuerpo no trae un evento");
			}
			evento.validar();                                                     // no cumple el contrato
		} catch (IOException | IllegalArgumentException veneno) {
			log.error("[consumidor] mensaje {} imposible de procesar ({}); va a la DLQ. Cuerpo: {}",
				mensaje.getMessageProperties().getMessageId(), veneno.getMessage(), cuerpo(mensaje));
			canal.basicNack(etiqueta, false, false);   // requeue=false: el broker lo manda al DLX
			return;
		}

		List<Notificacion> avisos;
		try {
			avisos = notificaciones.avisarDeTransferencia(evento);   // aqui hace commit
		} catch (RuntimeException fallaNuestra) {
			// El mensaje esta bien; fallo algo de este lado (la base, por ejemplo). Se reencola para
			// reintentarlo. La pausa evita un bucle que gire a toda velocidad mientras la base no vuelve.
			log.warn("[consumidor] no se pudo guardar el evento {} ({}); se reencola",
				evento.eventoId(), fallaNuestra.getMessage());
			esperar(1000);
			canal.basicNack(etiqueta, false, true);
			return;
		}
		log.info("[consumidor] {} de la transferencia {} (evento {}, redelivered={}): {} aviso(s) nuevo(s)",
			evento.tipo(), evento.transferenciaId(), evento.eventoId(), reentregado, avisos.size());

		pausaDeDemo();                     // tarea S09: ventana para matar el proceso con el mensaje Unacked
		canal.basicAck(etiqueta, false);   // DESPUES de guardar: "ya lo tengo, borralo"
	}

	/**
	 * Solo para la tarea del duplicado: abre una ventana ENTRE el commit de los avisos y el ack. Si en
	 * esa ventana se mata el contenedor, los avisos ya estan en la base pero el broker no recibio el
	 * ack: el mensaje regresa a Ready y se vuelve a entregar con redelivered=true.
	 * Con PAUSA_ANTES_DEL_ACK_MS=0 (lo normal) no hace nada.
	 */
	private void pausaDeDemo() {
		if (pausaAntesDelAckMs <= 0) {
			return;
		}
		log.info("[consumidor] pausa de {} ms antes del ack: es el momento de matar el contenedor", pausaAntesDelAckMs);
		esperar(pausaAntesDelAckMs);
	}

	private static void esperar(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException interrumpido) {
			Thread.currentThread().interrupt();
		}
	}

	/** Para el log: el cuerpo como texto, recortado. */
	private static String cuerpo(Message mensaje) {
		String texto = new String(mensaje.getBody(), StandardCharsets.UTF_8);
		return texto.length() > 300 ? texto.substring(0, 300) + "..." : texto;
	}
}
