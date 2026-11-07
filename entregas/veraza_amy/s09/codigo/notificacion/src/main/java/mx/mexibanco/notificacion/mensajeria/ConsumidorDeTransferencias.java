package mx.mexibanco.notificacion.mensajeria;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.*;
import java.io.IOException;
@Component
public class ConsumidorDeTransferencias {
    private static final Logger log = LoggerFactory.getLogger(ConsumidorDeTransferencias.class);
    private final ObjectMapper mapper;
    private final AvisadorDeTransferencias avisador;
    private final long pausa;
    public ConsumidorDeTransferencias(ObjectMapper mapper, AvisadorDeTransferencias avisador,
        @Value("${mexibanco.demo.pausa-antes-del-ack-ms:0}") long pausa) {
        this.mapper=mapper; this.avisador=avisador; this.pausa=pausa;
    }
    @RabbitListener(queues="notificacion.transferencias", ackMode="MANUAL")
    public void recibir(Message mensaje, Channel canal) throws IOException {
        long etiqueta = mensaje.getMessageProperties().getDeliveryTag();
        EventoTransferencia evento;
        try {
            evento = mapper.readValue(mensaje.getBody(), EventoTransferencia.class);
            if (evento == null) throw new IllegalArgumentException("Evento nulo");
            evento.validar();
        } catch (IOException | IllegalArgumentException invalido) {
            log.warn("Mensaje invalido a DLQ: {}", invalido.getMessage());
            canal.basicNack(etiqueta, false, false);
            return;
        }
        int nuevos;
        try {
            nuevos = avisador.avisar(evento); // El proxy confirma marca y avisos ANTES de regresar.
        } catch (DataAccessException | TransactionException temporal) {
            log.warn("Base no disponible; se reencola {}", evento.eventoId(), temporal);
            canal.basicNack(etiqueta, false, true);
            return;
        }
        log.info("{} de transferencia {} ({}, redelivered={}): {} aviso(s) nuevo(s)",
            evento.tipo(), evento.transferenciaId(), evento.eventoId(),
            mensaje.getMessageProperties().isRedelivered(), nuevos);
        try { if (pausa > 0) Thread.sleep(pausa); }
        catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            canal.basicNack(etiqueta, false, true);
            return;
        }
        canal.basicAck(etiqueta, false);
    }
}
