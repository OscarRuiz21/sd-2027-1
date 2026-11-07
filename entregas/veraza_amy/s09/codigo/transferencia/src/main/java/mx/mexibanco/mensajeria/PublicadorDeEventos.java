package mx.mexibanco.mensajeria;
import mx.mexibanco.transferencia.Transferencia;
import org.springframework.stereotype.Component;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.slf4j.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
@Component
public class PublicadorDeEventos {
    private static final Logger log = LoggerFactory.getLogger(PublicadorDeEventos.class);
    private final RabbitTemplate rabbit;
    public PublicadorDeEventos(RabbitTemplate rabbit) { this.rabbit = rabbit; }
    public void publicar(Transferencia t, String tipo, BigDecimal saldoOrigen, BigDecimal saldoDestino) {
        // Identidad estable aun si el mismo hecho se publica de nuevo tras un reinicio.
        UUID id = UUID.nameUUIDFromBytes((t.clave(tipo)).getBytes(StandardCharsets.UTF_8));
        EventoTransferencia evento = new EventoTransferencia(id, tipo, t.getId(), t.getClaveOrigen(),
            t.getClaveDestino(), t.getMonto(), saldoOrigen, saldoDestino, t.getActualizada());
        String key = "TransferenciaRealizada".equals(tipo) ? "transferencia.realizada" : "transferencia.revertida";
        CorrelationData confirmacion = new CorrelationData(id.toString());
        try {
            rabbit.convertAndSend("transferencias", key, evento, mensaje -> {
                mensaje.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                mensaje.getMessageProperties().setMessageId(id.toString());
                return mensaje;
            }, confirmacion);
            var resultado = confirmacion.getFuture().get(5, TimeUnit.SECONDS);
            if (!resultado.isAck() || confirmacion.getReturned() != null) {
                throw new IllegalStateException("Sin confirmacion o sin cola enlazada");
            }
            log.info("Evento {} confirmado: {}", id, key);
        } catch (Exception fallo) {
            if (fallo instanceof InterruptedException) Thread.currentThread().interrupt();
            // Sin outbox: no romper una transferencia que ya movio el dinero. Se documenta la perdida.
            log.error("Evento {} NO publicado; requiere recuperacion manual: {}", id, fallo.toString());
        }
    }
}
