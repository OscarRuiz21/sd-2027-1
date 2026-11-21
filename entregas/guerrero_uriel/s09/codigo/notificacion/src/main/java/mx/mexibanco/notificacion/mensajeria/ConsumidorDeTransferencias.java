package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import mx.mexibanco.notificacion.NotificacionService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ConsumidorDeTransferencias {

	private final ObjectMapper mapper;
	private final NotificacionService notificaciones;

	public ConsumidorDeTransferencias(ObjectMapper mapper, NotificacionService notificaciones) {
		this.mapper = mapper;
		this.notificaciones = notificaciones;
	}

	@RabbitListener(queues = "notificacion.transferencias")
	public void consumir(Message mensaje, Channel canal) throws IOException {
		long tag = mensaje.getMessageProperties().getDeliveryTag();
		try {
			EventoTransferencia evento = mapper.readValue(mensaje.getBody(), EventoTransferencia.class);
			validar(evento);
			notificaciones.procesar(evento);
			// Se confirma después de que la transacción con la bandeja terminó.
			canal.basicAck(tag, false);
		} catch (JsonProcessingException | IllegalArgumentException invalido) {
			// Un evento ilegible o fuera del contrato no mejorará al reintentarlo: va a la DLQ.
			canal.basicNack(tag, false, false);
		} catch (Exception fallaTransitoria) {
			// Un fallo de base de datos sí puede recuperarse; reencolar permite intentar de nuevo.
			canal.basicNack(tag, false, true);
			throw fallaTransitoria;
		}
	}

	private void validar(EventoTransferencia evento) {
		if (evento.eventoId() == null || evento.tipo() == null || evento.clabeOrigen() == null || evento.monto() == null) {
			throw new IllegalArgumentException("Evento incompleto");
		}
		if (!"TransferenciaRealizada".equals(evento.tipo()) && !"TransferenciaRevertida".equals(evento.tipo())) {
			throw new IllegalArgumentException("Tipo de evento desconocido: " + evento.tipo());
		}
		if ("TransferenciaRealizada".equals(evento.tipo()) && evento.clabeDestino() == null) {
			throw new IllegalArgumentException("Falta la CLABE destino");
		}
	}
}
