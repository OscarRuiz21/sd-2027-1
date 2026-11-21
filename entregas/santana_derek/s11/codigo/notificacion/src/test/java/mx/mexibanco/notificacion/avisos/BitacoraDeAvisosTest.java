package mx.mexibanco.notificacion.avisos;

import mx.mexibanco.notificacion.EventoTransferencia;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BitacoraDeAvisosTest {
    static EventoTransferencia evento() {
        return new EventoTransferencia(UUID.fromString("11111111-2222-4333-8444-555555555555"),
            EventoTransferencia.REALIZADA, 1L, "002180000000000001", "002180000000000002",
            new BigDecimal("0.10"), new BigDecimal("999.90"), new BigDecimal("500.10"),
            Instant.parse("2026-11-03T13:00:00Z"));
    }
    @Test void cadaCanalConservaDineroExactoYSuPropiaForma() {
        var e = evento(); var d = BitacoraDeAvisos.destinatarios(e).getFirst();
        var sms = BitacoraDeAvisos.documento(e,d,new Contacto.Sms("555"));
        var correo = BitacoraDeAvisos.documento(e,d,new Contacto.Correo("demo@example.test"));
        var push = BitacoraDeAvisos.documento(e,d,new Contacto.Push("demo"));
        assertThat(sms.get("monto")).isEqualTo(new Decimal128(new BigDecimal("0.10")));
        assertThat(sms).containsKeys("telefono","texto").doesNotContainKeys("correo","token");
        assertThat(correo).containsKeys("correo","asunto","cuerpo").doesNotContainKeys("telefono","token");
        assertThat(push).containsKeys("token","titulo","cuerpo").doesNotContainKeys("telefono","correo");
        assertThat(sms.getString("_id")).isEqualTo(e.eventoId()+":"+d.clabe());
    }
    @Test void unDuplicadoParcialNoImpideGuardarElOtroDestinatario() {
        var mongo=mock(MongoTemplate.class);
        when(mongo.insert(any(Document.class),eq("avisos")))
            .thenThrow(new DuplicateKeyException("origen ya guardado"))
            .thenAnswer(inv -> inv.getArgument(0));
        var b=new BitacoraDeAvisos(mongo,new DirectorioDeContactos());
        assertThat(b.registrar(evento())).isEqualTo(1);
        verify(mongo,times(2)).insert(any(Document.class),eq("avisos"));
    }
    @Test void unaDevolucionAvisaSoloAlOrigen() {
        var e=evento();
        var r=new EventoTransferencia(e.eventoId(),EventoTransferencia.REVERTIDA,e.transferenciaId(),
            e.clabeOrigen(),e.clabeDestino(),e.monto(),e.saldoOrigen(),null,e.fecha());
        assertThat(BitacoraDeAvisos.destinatarios(r)).hasSize(1);
        assertThat(BitacoraDeAvisos.destinatarios(r).getFirst().clabe()).isEqualTo(e.clabeOrigen());
    }
}
