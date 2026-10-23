package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import mx.mexibanco.notificacion.NotificacionService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConsumidorDeTransferenciasTest {
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final Channel canal = mock(Channel.class);
    private final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();
    private final ConsumidorDeTransferencias consumidor = new ConsumidorDeTransferencias(notificaciones, json);

    @Test
    void confirmaLuegoDeGuardar() throws Exception {
        when(notificaciones.avisarDeTransferencia(any())).thenReturn(List.of());

        consumidor.recibir(mensaje("""
                {"eventoId":"8a1f2c3e-5b7d-4e9a-9c0f-1d2e3f4a5b6c","tipo":"TransferenciaRealizada",
                 "transferenciaId":42,"clabeOrigen":"002180000000000001","clabeDestino":"002180000000000002",
                 "monto":200.00,"saldoOrigen":800.00,"saldoDestino":700.00,"fecha":"2026-10-17T10:42:07Z"}
                """), canal);

        var orden = inOrder(notificaciones, canal);
        orden.verify(notificaciones).avisarDeTransferencia(any());
        orden.verify(canal).basicAck(7L, false);
        verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void jsonInvalidoVaALaDlq() throws Exception {
        consumidor.recibir(mensaje("{\"hola\":"), canal);
        verify(canal).basicNack(7L, false, false);
        verifyNoInteractions(notificaciones);
    }

    @Test
    void eventoSinIdVaALaDlq() throws Exception {
        consumidor.recibir(mensaje("{\"tipo\":\"TransferenciaRealizada\"}"), canal);
        verify(canal).basicNack(7L, false, false);
        verifyNoInteractions(notificaciones);
    }

    @Test
    void falloDeBaseSeReencola() throws Exception {
        when(notificaciones.avisarDeTransferencia(any())).thenThrow(new IllegalStateException("base no disponible"));
        consumidor.recibir(mensaje("""
                {"eventoId":"8a1f2c3e-5b7d-4e9a-9c0f-1d2e3f4a5b6c","tipo":"TransferenciaRevertida",
                 "transferenciaId":42,"clabeOrigen":"002180000000000001","clabeDestino":"002180000000000002",
                 "monto":200.00,"saldoOrigen":1000.00,"fecha":"2026-10-17T10:42:07Z"}
                """), canal);
        verify(canal).basicNack(7L, false, true);
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
    }

    private static Message mensaje(String cuerpo) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setDeliveryTag(7L);
        return new Message(cuerpo.getBytes(java.nio.charset.StandardCharsets.UTF_8), propiedades);
    }
}
