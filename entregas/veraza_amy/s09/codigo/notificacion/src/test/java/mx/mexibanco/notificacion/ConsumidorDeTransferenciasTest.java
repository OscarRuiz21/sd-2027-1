package mx.mexibanco.notificacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import mx.mexibanco.notificacion.mensajeria.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.dao.DataAccessResourceFailureException;
import java.nio.charset.StandardCharsets;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ConsumidorDeTransferenciasTest {
    private final AvisadorDeTransferencias avisador = mock(AvisadorDeTransferencias.class);
    private final Channel canal = mock(Channel.class);
    private final ConsumidorDeTransferencias consumidor = new ConsumidorDeTransferencias(
        new ObjectMapper().findAndRegisterModules(), avisador, 0);

    private Message mensaje(String json) {
        MessageProperties p = new MessageProperties();
        p.setDeliveryTag(7);
        return new Message(json.getBytes(StandardCharsets.UTF_8), p);
    }
    private String valido() {
        return """
            {"eventoId":"8a1f2c3e-5b7d-4e9a-9c0f-1d2e3f4a5b6c",
            "tipo":"TransferenciaRealizada","transferenciaId":42,
            "clabeOrigen":"002180000000000001","clabeDestino":"002180000000000002",
            "monto":200,"saldoOrigen":800,"saldoDestino":700,"fecha":"2026-10-17T10:42:07Z"}
            """;
    }
    @Test void confirmaSolamenteDespuesDeGuardar() throws Exception {
        consumidor.recibir(mensaje(valido()), canal);
        var orden = inOrder(avisador, canal);
        orden.verify(avisador).avisar(any());
        orden.verify(canal).basicAck(7, false);
    }
    @Test void jsonRotoVaADlqSinTocarLaBase() throws Exception {
        consumidor.recibir(mensaje("{\"hola\":"), canal);
        verify(canal).basicNack(7, false, false);
        verifyNoInteractions(avisador);
    }
    @Test void jsonBienFormadoSinContratoTambienVaADlq() throws Exception {
        consumidor.recibir(mensaje("{}"), canal);
        verify(canal).basicNack(7, false, false);
        verifyNoInteractions(avisador);
    }
    @Test void errorDeBaseReencolaSinConfirmar() throws Exception {
        when(avisador.avisar(any())).thenThrow(new DataAccessResourceFailureException("caida"));
        consumidor.recibir(mensaje(valido()), canal);
        verify(canal).basicNack(7, false, true);
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
    }
}
