package mx.mexibanco.notificacion.avisos;
import mx.mexibanco.notificacion.EventoTransferencia;
import org.junit.jupiter.api.Test;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.dao.DuplicateKeyException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
class BitacoraDeAvisosTest {
    EventoTransferencia evento() {
        return new EventoTransferencia(UUID.randomUUID(),"TransferenciaRealizada",12L,
            "002180000000000001","002180000000000002",new BigDecimal("200.10"),
            new BigDecimal("799.90"),new BigDecimal("700.10"),Instant.parse("2026-10-31T13:00:00Z"));
    }
    @Test void conservaDecimalesYCamposPorCanal() {
        var e=evento(); var d=BitacoraDeAvisos.destinatarios(e).getFirst();
        var sms=BitacoraDeAvisos.documento(e,d,new Contacto.Sms("555"));
        var correo=BitacoraDeAvisos.documento(e,d,new Contacto.Correo("a@b.mx"));
        var push=BitacoraDeAvisos.documento(e,d,new Contacto.Push("token"));
        assertThat(sms.get("_id")).isEqualTo(e.eventoId()+":"+d.clabe());
        assertThat(sms.get("monto")).isEqualTo(new Decimal128(new BigDecimal("200.10")));
        assertThat(sms).containsEntry("canal","SMS").containsKey("telefono").doesNotContainKeys("correo","token");
        assertThat(correo).containsEntry("canal","CORREO").containsKeys("correo","asunto","cuerpo").doesNotContainKey("telefono");
        assertThat(push).containsEntry("canal","PUSH").containsKeys("token","titulo","cuerpo").doesNotContainKey("correo");
    }
    @Test void unFalloParcialSePuedeReintentarSinPerderAlSegundoDestinatario() {
        var mongo=mock(MongoTemplate.class); var b=new BitacoraDeAvisos(mongo,new DirectorioDeContactos());
        when(mongo.insert(any(Document.class),eq("avisos")))
            .thenThrow(new DuplicateKeyException("primer aviso ya guardado"))
            .thenReturn(new Document());
        assertThat(b.registrar(evento())).isEqualTo(1);
        verify(mongo,times(2)).insert(any(Document.class),eq("avisos"));
    }
}
