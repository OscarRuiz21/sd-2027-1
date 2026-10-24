package mx.mexibanco.mensajeria;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PublicadorDeEventos {
    private final RabbitTemplate rabbit;
    private final long esperaMs;

    public PublicadorDeEventos(RabbitTemplate rabbit,
            @Value("${mexibanco.mensajeria.espera-confirmacion-ms:5000}") long esperaMs) {
        this.rabbit = rabbit;
        this.esperaMs = esperaMs;
    }

    public void publicar(EventoTransferencia evento) {
        String id = evento.eventoId().toString();
        CorrelationData correlacion = new CorrelationData(id);
        try {
            rabbit.convertAndSend(ConfiguracionMensajeria.EXCHANGE, evento.routingKey(),
                    evento, mensaje -> {
                        mensaje.getMessageProperties().setMessageId(id);
                        mensaje.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return mensaje;
                    }, correlacion);
            CorrelationData.Confirm confirmacion = correlacion.getFuture()
                    .get(esperaMs, TimeUnit.MILLISECONDS);
            if (!confirmacion.isAck()) {
                throw new EventoNoPublicadoException("Nack para " + id + ": " + confirmacion.getReason());
            }
            if (correlacion.getReturned() != null) {
                throw new EventoNoPublicadoException("Evento " + id + " sin cola: "
                        + correlacion.getReturned().getReplyText());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventoNoPublicadoException("Espera interrumpida: " + id, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new EventoNoPublicadoException("Confirmación incierta: " + id, e);
        } catch (RuntimeException e) {
            throw new EventoNoPublicadoException("Publicación fallida: " + id, e);
        }
    }
}