package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.mexibanco.transferencia.Transferencia;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicadorDeTransferenciasTest {
    private Transferencia terminada(boolean revertida) {
        Transferencia t = new Transferencia("002180000000000001", "002180000000000002", new BigDecimal("200"));
        ReflectionTestUtils.setField(t, "id", 42L);
        t.marcarCargada(new BigDecimal("800"));
        if (revertida) {
            t.marcarCompensada("falla de abono");
            t.prepararEvento(new BigDecimal("1000"), new BigDecimal("500"));
        } else {
            t.marcarAbonada();
            t.marcarCompletada();
            t.prepararEvento(new BigDecimal("800"), new BigDecimal("700"));
        }
        return t;
    }

    @Test
    void republicarConservaIdCuerpoYRutaDelEvento() throws Exception {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        PublicadorDeTransferencias publicador = new PublicadorDeTransferencias(rabbit);
        Transferencia t = terminada(false);
        publicador.publicar(t);
        t.prepararEvento(BigDecimal.ZERO, BigDecimal.ZERO); // no cambia el evento ya preparado
        publicador.publicar(t);

        ArgumentCaptor<EventoTransferencia> eventos = ArgumentCaptor.forClass(EventoTransferencia.class);
        ArgumentCaptor<MessagePostProcessor> procesador = ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlacion = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbit, times(2)).convertAndSend(eq("transferencias"), eq("transferencia.realizada"),
            eventos.capture(), procesador.capture(), correlacion.capture());
        EventoTransferencia evento = eventos.getAllValues().get(0);
        assertThat(eventos.getAllValues().get(1)).isEqualTo(evento);
        assertThat(evento.tipo()).isEqualTo("TransferenciaRealizada");
        assertThat(evento.saldoOrigen()).isEqualByComparingTo("800");
        assertThat(correlacion.getValue().getId()).isEqualTo(evento.eventoId());
        Message mensaje = procesador.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertThat(mensaje.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(mensaje.getMessageProperties().getMessageId()).isEqualTo(evento.eventoId());

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var converter = new RabbitConfig().conversorJson(mapper);
        var json = mapper.readTree(converter.toMessage(evento, new MessageProperties()).getBody());
        HashSet<String> campos = new HashSet<>();
        json.fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactlyInAnyOrder("eventoId", "tipo", "transferenciaId", "clabeOrigen",
            "clabeDestino", "monto", "saldoOrigen", "saldoDestino", "fecha");
        assertThat(json.get("fecha").isTextual()).isTrue();
        assertThat(json.get("fecha").asText()).isEqualTo(t.getActualizada().toString());
    }

    @Test
    void unaCompensadaPublicaElTipoYLaClaveDeReversion() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        new PublicadorDeTransferencias(rabbit).publicar(terminada(true));
        ArgumentCaptor<EventoTransferencia> evento = ArgumentCaptor.forClass(EventoTransferencia.class);
        verify(rabbit).convertAndSend(eq("transferencias"), eq("transferencia.revertida"),
            evento.capture(), any(MessagePostProcessor.class), any(CorrelationData.class));
        assertThat(evento.getValue().tipo()).isEqualTo("TransferenciaRevertida");
        assertThat(evento.getValue().saldoOrigen()).isEqualByComparingTo("1000");
    }
}
