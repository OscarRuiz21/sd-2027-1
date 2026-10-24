package mx.mexibanco.transferencia;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final EventoOutboxRepository outboxRepo;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OutboxRelay(EventoOutboxRepository outboxRepo, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.outboxRepo = outboxRepo;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publicarEventosPendientes() {
        List<EventoOutbox> pendientes = outboxRepo.findPendientesParaProcesar();
        for (EventoOutbox evento : pendientes) {
            try {
                // SE CONVIERTE el JSON a objeto para mandarlo al Exchange
                TransferenciaEvento payload = objectMapper.readValue(evento.getPayload(), TransferenciaEvento.class);
                rabbitTemplate.convertAndSend("transferencias", evento.getRoutingKey(), payload);
                
                // SE MARCA como procesado
                evento.setProcesado(true);
                outboxRepo.save(evento);
                log.info("Evento enviado desde Outbox a RabbitMQ: {}", evento.getId());
            } catch (Exception e) {
                log.error("Error al enviar evento outbox {}: {}", evento.getId(), e.getMessage());
            }
        }
    }
}