
package mx.mexibanco.mensajeria;

import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class PublicadorDeEventos {

    private final RabbitTemplate rabbit;
    private final long esperaConfirmacionMs;

    public PublicadorDeEventos(
            RabbitTemplate rabbit,
            @Value("${mexibanco.mensajeria.espera-confirmacion-ms:5000}")
            long esperaConfirmacionMs) {

        this.rabbit = rabbit;
        this.esperaConfirmacionMs = esperaConfirmacionMs;
    }

    /**
     * Publica un evento y espera la confirmacion del broker.
     */
    public void publicar(EventoTransferencia evento) {

        String id = evento.eventoId().toString();
        CorrelationData correlacion = new CorrelationData(id);

        try {
            rabbit.convertAndSend(
                ConfiguracionMensajeria.EXCHANGE,
                evento.routingKey(),
                evento,
                mensaje -> {
                    mensaje.getMessageProperties()
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT);

                    mensaje.getMessageProperties().setMessageId(id);

                    return mensaje;
                },
                correlacion
            );

        } catch (RuntimeException sinConexion) {
            throw new EventoNoPublicadoException(
                "No se pudo mandar el evento " + id
                    + " al broker: " + sinConexion.getMessage(),
                sinConexion
            );
        }

        CorrelationData.Confirm confirmacion;

        try {
            confirmacion = correlacion.getFuture()
                .get(esperaConfirmacionMs, TimeUnit.MILLISECONDS);

        } catch (TimeoutException | ExecutionException sinRespuesta) {
            throw new EventoNoPublicadoException(
                "El broker no confirmo el evento " + id
                    + ": " + sinRespuesta,
                sinRespuesta
            );

        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();

            throw new EventoNoPublicadoException(
                "Se interrumpio la espera del evento " + id,
                interrumpido
            );
        }

        if (!confirmacion.isAck()) {
            throw new EventoNoPublicadoException(
                "El broker rechazo el evento " + id
                    + " (nack): " + confirmacion.getReason()
            );
        }

        ReturnedMessage regresado = correlacion.getReturned();

        if (regresado != null) {
            throw new EventoNoPublicadoException(
                "Ninguna cola recibio el evento " + id
                    + " con la routing key "
                    + regresado.getRoutingKey()
                    + " (" + regresado.getReplyText() + ")"
            );
        }
    }
}
