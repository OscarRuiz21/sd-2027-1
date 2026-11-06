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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BitacoraDeAvisosTest {
    private final EventoTransferencia evento = new EventoTransferencia(UUID.randomUUID(),
        "TransferenciaRealizada", 42L, "002180000000000001", "002180000000000002",
        new BigDecimal("12.34"), new BigDecimal("987.66"), new BigDecimal("512.34"), Instant.parse("2026-11-06T08:00:00Z"));

    @Test void conservaPrecisionYFormaSegunCanal() {
        var d = BitacoraDeAvisos.destinatarios(evento).getFirst();
        Document sms = BitacoraDeAvisos.documento(evento, d, new Contacto.Sms("555"));
        Document correo = BitacoraDeAvisos.documento(evento, d, new Contacto.Correo("demo@example.test"));
        Document push = BitacoraDeAvisos.documento(evento, d, new Contacto.Push("token-demo"));
        assertThat(sms.get("monto")).isEqualTo(new Decimal128(new BigDecimal("12.34")));
        assertThat(sms).containsKeys("telefono", "texto").doesNotContainKeys("correo", "token");
        assertThat(correo).containsKeys("correo", "asunto", "cuerpo").doesNotContainKeys("telefono", "token");
        assertThat(push).containsKeys("token", "titulo", "cuerpo").doesNotContainKeys("telefono", "correo");
    }

    @Test void completaElSegundoAvisoSiElPrimeroYaEstaba() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.insert(any(Document.class), eq("avisos")))
            .thenThrow(new DuplicateKeyException("primer aviso confirmado antes de caer"))
            .thenAnswer(i -> i.getArgument(0));
        assertThat(new BitacoraDeAvisos(mongo, new DirectorioDeContactos()).registrar(evento)).isEqualTo(1);
        verify(mongo, times(2)).insert(any(Document.class), eq("avisos"));
    }

    @Test void unaCaidaRealDeMongoNoSeConfundeConDuplicado() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.insert(any(Document.class), eq("avisos"))).thenThrow(new IllegalStateException("sin conexion"));
        assertThatThrownBy(() -> new BitacoraDeAvisos(mongo, new DirectorioDeContactos()).registrar(evento))
            .isInstanceOf(IllegalStateException.class);
    }
}
