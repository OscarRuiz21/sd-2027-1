package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import mx.mexibanco.notificacion.EventoTransferencia;
import mx.mexibanco.notificacion.NotificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsumidorDeTransferenciasTest {
    @Mock NotificacionService notificaciones;
    @Mock ObjectMapper json;
    @Mock Channel canal;
    @InjectMocks ConsumidorDeTransferencias consumidor;
    Message mensaje;
    EventoTransferencia evento;

    @BeforeEach
    void preparar() throws Exception {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setDeliveryTag(7L);
        mensaje = new Message(new byte[0], propiedades);
        evento = new EventoTransferencia(UUID.randomUUID(), EventoTransferencia.REALIZADA,
                42L, "002180000000000001", "002180000000000002", new BigDecimal("200"),
                new BigDecimal("800"), new BigDecimal("700"), Instant.now());
        when(json.readValue(mensaje.getBody(), EventoTransferencia.class)).thenReturn(evento);
    }

    @Test
    void confirmaSoloDespuesDeQueElServicioRegresa() throws Exception {
        when(notificaciones.avisarDeTransferencia(evento)).thenReturn(List.of());

        consumidor.recibir(mensaje, canal);

        InOrder orden = inOrder(notificaciones, canal);
        orden.verify(notificaciones).avisarDeTransferencia(evento);
        orden.verify(canal).basicAck(7L, false);
    }

    @Test
    void unaFallaAlGuardarNoConfirmaLaEntrega() {
        when(notificaciones.avisarDeTransferencia(evento))
                .thenThrow(new IllegalStateException("No se pudo confirmar la transaccion"));

        assertThatThrownBy(() -> consumidor.recibir(mensaje, canal))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(canal);
    }
}
