package mx.mexibanco.notificacion.avisos;

import mx.mexibanco.notificacion.EventoTransferencia;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BitacoraDeAvisosTest {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final BitacoraDeAvisos bitacora = new BitacoraDeAvisos(mongo, new DirectorioDeContactos());
    private final EventoTransferencia evento = new EventoTransferencia(UUID.randomUUID(),
        EventoTransferencia.REALIZADA, 1L, "002180000000000001", "002180000000000002",
        new BigDecimal("20.25"), new BigDecimal("979.75"), new BigDecimal("520.25"),
        Instant.parse("2026-11-02T12:00:00Z"));

    @Test
    void losTresCanalesConservanDineroYFechaSinCamposAjenos() {
        var destinatario = BitacoraDeAvisos.destinatarios(evento).getFirst();
        Document sms = BitacoraDeAvisos.documento(evento, destinatario, new Contacto.Sms("5550000000"));
        Document correo = BitacoraDeAvisos.documento(evento, destinatario, new Contacto.Correo("ana@example.test"));
        Document push = BitacoraDeAvisos.documento(evento, destinatario, new Contacto.Push("token"));
        for (Document documento : List.of(sms, correo, push)) {
            assertThat(documento.get("_id")).isEqualTo(evento.eventoId() + ":" + destinatario.clabe());
            assertThat(documento.get("monto")).isEqualTo(new Decimal128(evento.monto()));
            assertThat(documento.get("saldo")).isEqualTo(new Decimal128(destinatario.saldo()));
            assertThat(documento.get("fecha")).isEqualTo(Date.from(evento.fecha()));
        }
        assertThat(sms).containsEntry("canal", "SMS").containsKeys("telefono", "texto").doesNotContainKeys("correo", "token");
        assertThat(correo).containsEntry("canal", "CORREO").containsKeys("correo", "asunto", "cuerpo").doesNotContainKey("telefono");
        assertThat(push).containsEntry("canal", "PUSH").containsKeys("token", "titulo", "cuerpo").doesNotContainKey("correo");
    }

    @Test
    void unPrimerAvisoRepetidoNoImpideGuardarElSegundo() {
        when(mongo.insert(any(Document.class), eq("avisos")))
            .thenThrow(new DuplicateKeyException("primero ya estaba")).thenReturn(new Document());
        assertThat(bitacora.registrar(evento)).isEqualTo(1);
        verify(mongo, times(2)).insert(any(Document.class), eq("avisos"));
    }

    @Test
    void dosRepetidosNoSeCuentanComoNuevos() {
        when(mongo.insert(any(Document.class), eq("avisos"))).thenThrow(new DuplicateKeyException("ya estaba"));
        assertThat(bitacora.registrar(evento)).isZero();
    }

    @Test
    void ultimosFiltraClabeOrdenaPorFechaYLimitaADiez() {
        when(mongo.find(any(Query.class), eq(Document.class), eq("avisos"))).thenReturn(List.of());
        bitacora.ultimos("002180000000000002");
        ArgumentCaptor<Query> consulta = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(consulta.capture(), eq(Document.class), eq("avisos"));
        assertThat(consulta.getValue().getQueryObject()).containsEntry("clabe", "002180000000000002");
        assertThat(consulta.getValue().getSortObject()).containsEntry("fecha", -1);
        assertThat(consulta.getValue().getLimit()).isEqualTo(10);
    }
}
