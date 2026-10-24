package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import mx.mexibanco.notificacion.Notificacion;
import mx.mexibanco.notificacion.NotificacionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MensajeriaTest {
    private EventoProcesadoRepository nuevoRegistro() {
        EventoProcesadoRepository registro = mock(EventoProcesadoRepository.class);
        when(registro.registrarSiNuevo(anyString())).thenReturn(1);
        return registro;
    }

    private EventoTransferencia evento(String tipo) {
        return new EventoTransferencia("8a1f2c3e-5b7d-4e9a-9c0f-1d2e3f4a5b6c", tipo, 42L,
            "002180000000000001", "002180000000000002", new BigDecimal("200"),
            new BigDecimal("800"), new BigDecimal("700"), Instant.parse("2026-10-17T10:42:07Z"));
    }

    @Test
    void realizadaGuardaUnAvisoParaCadaCuenta() {
        NotificacionRepository repo = mock(NotificacionRepository.class);
        new ProcesadorDeTransferencias(repo, nuevoRegistro()).procesar(evento("TransferenciaRealizada"));
        ArgumentCaptor<Notificacion> avisos = ArgumentCaptor.forClass(Notificacion.class);
        verify(repo, times(2)).save(avisos.capture());
        assertThat(avisos.getAllValues()).extracting(Notificacion::getClabeCuenta)
            .containsExactly("002180000000000001", "002180000000000002");
        assertThat(avisos.getAllValues().get(0).getMensaje()).contains("#42", "enviada", "200");
        assertThat(avisos.getAllValues().get(1).getMensaje()).contains("#42", "recibida", "700");
    }

    @Test
    void revertidaSoloAvisaAOrigen() {
        NotificacionRepository repo = mock(NotificacionRepository.class);
        new ProcesadorDeTransferencias(repo, nuevoRegistro()).procesar(evento("TransferenciaRevertida"));
        ArgumentCaptor<Notificacion> aviso = ArgumentCaptor.forClass(Notificacion.class);
        verify(repo).save(aviso.capture());
        assertThat(aviso.getValue().getClabeCuenta()).isEqualTo("002180000000000001");
        assertThat(aviso.getValue().getMensaje()).contains("no se realizó", "te devolvimos el dinero", "#42");
    }

    @Test
    void tipoDesconocidoNoGuardaAvisos() {
        NotificacionRepository repo = mock(NotificacionRepository.class);
        assertThatThrownBy(() -> new ProcesadorDeTransferencias(repo, nuevoRegistro()).procesar(evento("Desconocido")))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repo);
    }

    @Test
    void confirmaSoloDespuesDeVolverDelProcesador() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ProcesadorDeTransferencias procesador = mock(ProcesadorDeTransferencias.class);
        when(procesador.procesar(any())).thenReturn(true);
        Channel canal = mock(Channel.class);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(7L);
        EventoTransferencia e = evento("TransferenciaRealizada");
        new ConsumidorDeTransferencias(mapper, procesador)
            .recibir(new Message(mapper.writeValueAsBytes(e), properties), canal);
        var orden = inOrder(procesador, canal);
        orden.verify(procesador).procesar(e);
        orden.verify(canal).basicAck(7L, false);
        verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void siFallaElGuardadoNoConfirmaYReencola() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ProcesadorDeTransferencias procesador = mock(ProcesadorDeTransferencias.class);
        doThrow(new IllegalStateException("base no disponible")).when(procesador).procesar(any());
        Channel canal = mock(Channel.class);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(8L);
        new ConsumidorDeTransferencias(mapper, procesador).recibir(
            new Message(mapper.writeValueAsBytes(evento("TransferenciaRealizada")), properties), canal);
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
        verify(canal).basicNack(8L, false, true);
    }

    @Test
    void duplicadoNoCreaOtroJuegoDeAvisos() {
        NotificacionRepository repo = mock(NotificacionRepository.class);
        EventoProcesadoRepository registro = mock(EventoProcesadoRepository.class);
        when(registro.registrarSiNuevo(anyString())).thenReturn(1, 0);
        var procesador = new ProcesadorDeTransferencias(repo, registro);
        EventoTransferencia e = evento("TransferenciaRealizada");
        assertThat(procesador.procesar(e)).isTrue();
        assertThat(procesador.procesar(e)).isFalse();
        verify(repo, times(2)).save(any());
    }

    @Test
    void duplicadoRevertidoNoCreaOtraDevolucionEnBandeja() {
        NotificacionRepository repo = mock(NotificacionRepository.class);
        EventoProcesadoRepository registro = mock(EventoProcesadoRepository.class);
        when(registro.registrarSiNuevo(anyString())).thenReturn(1, 0);
        var procesador = new ProcesadorDeTransferencias(repo, registro);
        EventoTransferencia e = evento("TransferenciaRevertida");
        assertThat(procesador.procesar(e)).isTrue();
        assertThat(procesador.procesar(e)).isFalse();
        verify(repo, times(1)).save(any());
    }

    @Test
    void duplicadoTambienSeConfirma() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ProcesadorDeTransferencias procesador = mock(ProcesadorDeTransferencias.class);
        when(procesador.procesar(any())).thenReturn(false);
        Channel canal = mock(Channel.class);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(9L);
        new ConsumidorDeTransferencias(mapper, procesador).recibir(
            new Message(mapper.writeValueAsBytes(evento("TransferenciaRealizada")), properties), canal);
        verify(canal).basicAck(9L, false);
        verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void falloAlGuardarSePropagaParaQueSpringReviertaLaTransaccion() {
        NotificacionRepository repo = mock(NotificacionRepository.class);
        when(repo.save(any())).thenThrow(new IllegalStateException("fallo de escritura"));
        var procesador = new ProcesadorDeTransferencias(repo, nuevoRegistro());
        assertThatThrownBy(() -> procesador.procesar(evento("TransferenciaRealizada")))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void jsonIncompletoSeRechazaSinReencolarNiGuardar() throws Exception {
        comprobarRechazo("{\"hola\":");
    }

    @Test
    void jsonNullSeRechazaSinReencolarNiGuardar() throws Exception {
        comprobarRechazo("null");
    }

    @Test
    void jsonLegibleSinContratoSeRechazaSinReencolarNiGuardar() throws Exception {
        comprobarRechazo("{}");
    }

    @Test
    void tipoDesconocidoVaADlqAntesDeLlamarAlProcesador() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        comprobarRechazo(mapper.writeValueAsString(evento("Desconocido")));
    }

    @Test
    void datosDespuesDelEventoSeRechazan() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        comprobarRechazo(mapper.writeValueAsString(evento("TransferenciaRealizada")) + " {}");
    }

    private void comprobarRechazo(String cuerpo) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ProcesadorDeTransferencias procesador = mock(ProcesadorDeTransferencias.class);
        Channel canal = mock(Channel.class);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(10L);
        new ConsumidorDeTransferencias(mapper, procesador).recibir(
            new Message(cuerpo.getBytes(java.nio.charset.StandardCharsets.UTF_8), properties), canal);
        verifyNoInteractions(procesador);
        verify(canal).basicNack(10L, false, false);
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
        verify(canal, never()).basicNack(anyLong(), anyBoolean(), eq(true));
    }
}
