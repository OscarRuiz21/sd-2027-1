package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ConsumidorDeTransferencias {
    private static final Logger log = LoggerFactory.getLogger(ConsumidorDeTransferencias.class);
    private final ObjectMapper mapper;
    private final ProcesadorDeTransferencias procesador;

    public ConsumidorDeTransferencias(ObjectMapper mapper, ProcesadorDeTransferencias procesador) {
        this.mapper = mapper;
        this.procesador = procesador;
    }

    @RabbitListener(queues = RabbitConfig.COLA, ackMode = "MANUAL")
    public void recibir(Message mensaje, Channel canal) throws IOException, InterruptedException {
        long tag = mensaje.getMessageProperties().getDeliveryTag();
        EventoTransferencia evento;
        boolean nuevo;
        try {
            // Se lee JSON directamente: no se confia en la cabecera de clase Java del productor.
            evento = mapper.readerFor(EventoTransferencia.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readValue(mensaje.getBody());
            if (evento == null) {
                throw new IllegalArgumentException("El evento no puede ser null");
            }
            evento.validar();
        } catch (IOException | IllegalArgumentException invalido) {
            // JSON ilegible o contrato invalido: repetirlo no va a corregir su contenido.
            // RabbitMQ lo enruta al DLX y a su DLQ; este listener no lo republica a mano.
            canal.basicNack(tag, false, false);
            log.warn("MENSAJE INVALIDO RECHAZADO A DLQ messageId={} causa={}; requeue=false",
                mensaje.getMessageProperties().getMessageId(), invalido.toString());
            return;
        }
        try {
            nuevo = procesador.procesar(evento); // al volver, la transaccion ya hizo commit
        } catch (RuntimeException fallo) {
            log.warn("NO PROCESADO messageId={} redelivered={} causa={}; requeue=true",
                mensaje.getMessageProperties().getMessageId(),
                mensaje.getMessageProperties().isRedelivered(), fallo.toString());
            // Un fallo al guardar no implica JSON invalido: se conserva para reintentar.
            // La pausa limita el bucle; en produccion se agregarian reintentos acotados/backoff.
            Thread.sleep(1000);
            canal.basicNack(tag, false, true);
            return;
        }
        // Fuera del catch: si falla el canal al confirmar, el broker puede volver a entregar.
        canal.basicAck(tag, false);
        log.info("{} eventoId={} transferenciaId={} tipo={} redelivered={}",
            nuevo ? "AVISOS GUARDADOS Y ACK" : "DUPLICADO OMITIDO Y ACK",
            evento.eventoId(), evento.transferenciaId(), evento.tipo(),
            mensaje.getMessageProperties().isRedelivered());
    }
}
