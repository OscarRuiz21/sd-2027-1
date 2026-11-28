package mx.mexibanco.notificacion.avisos;

import mx.mexibanco.notificacion.EventoTransferencia;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * v11: la forma de cada aviso en MongoDB y que un evento repetido no duplique avisos. MongoTemplate es
 * un doble: lo que se prueba es que documento se arma y que pasa con la llave duplicada.
 */
@ExtendWith(MockitoExtension.class)
class BitacoraDeAvisosTest {

	private static final String ANA = "002180000000000001";
	private static final String BETO = "002180000000000002";
	private static final String CARLA = "002180000000000003";
	private static final UUID EVENTO = UUID.fromString("8a1f2c3e-5b7d-4e9a-9c0f-1d2e3f4a5b6c");

	@Mock
	MongoTemplate mongo;

	private final DirectorioDeContactos contactos = new DirectorioDeContactos();

	private static EventoTransferencia realizada(String origen, String destino) {
		return new EventoTransferencia(EVENTO, EventoTransferencia.REALIZADA, 42L, origen, destino,
			new BigDecimal("200.00"), new BigDecimal("800.00"), new BigDecimal("700.00"), Instant.parse("2026-10-17T10:42:07Z"));
	}

	@Test
	void losCamposComunesYElIdSonDelContrato() {
		EventoTransferencia evento = realizada(ANA, BETO);
		BitacoraDeAvisos.Destinatario beto = BitacoraDeAvisos.destinatarios(evento).get(1);

		Document aviso = BitacoraDeAvisos.documento(evento, beto, contactos.de(BETO));

		assertThat(aviso.get("_id")).isEqualTo(EVENTO + ":" + BETO);
		assertThat(aviso.get("eventoId")).isEqualTo(EVENTO.toString());
		assertThat(aviso.get("transferenciaId")).isEqualTo(42L);
		assertThat(aviso.get("clabe")).isEqualTo(BETO);
		assertThat(aviso.get("tipo")).isEqualTo("TRANSFERENCIA_RECIBIDA");
		assertThat(aviso.get("monto")).isEqualTo(new Decimal128(new BigDecimal("200.00")));
		assertThat(aviso.get("saldo")).isEqualTo(new Decimal128(new BigDecimal("700.00")));
		assertThat(aviso.get("fecha")).isEqualTo(Date.from(Instant.parse("2026-10-17T10:42:07Z")));
	}

	@Test
	void anaRecibeSmsBetoCorreoYCarlaPush() {
		EventoTransferencia aBeto = realizada(ANA, BETO);
		EventoTransferencia aCarla = realizada(ANA, CARLA);

		Document sms = BitacoraDeAvisos.documento(aBeto, BitacoraDeAvisos.destinatarios(aBeto).get(0), contactos.de(ANA));
		Document correo = BitacoraDeAvisos.documento(aBeto, BitacoraDeAvisos.destinatarios(aBeto).get(1), contactos.de(BETO));
		Document push = BitacoraDeAvisos.documento(aCarla, BitacoraDeAvisos.destinatarios(aCarla).get(1), contactos.de(CARLA));

		assertThat(sms).containsEntry("canal", "SMS").containsKeys("telefono", "texto").doesNotContainKeys("correo", "token");
		assertThat(correo).containsEntry("canal", "CORREO").containsKeys("correo", "asunto", "cuerpo").doesNotContainKeys("telefono", "token");
		assertThat(push).containsEntry("canal", "PUSH").containsKeys("token", "titulo", "cuerpo").doesNotContainKeys("telefono", "correo");
	}

	@Test
	void unaRealizadaGuardaDosAvisos() {
		BitacoraDeAvisos bitacora = new BitacoraDeAvisos(mongo, contactos);

		int nuevos = bitacora.registrar(realizada(ANA, BETO));

		assertThat(nuevos).isEqualTo(2);
		ArgumentCaptor<Document> avisos = ArgumentCaptor.forClass(Document.class);
		verify(mongo, times(2)).insert(avisos.capture(), eq(BitacoraDeAvisos.COLECCION));
		assertThat(avisos.getAllValues()).extracting(d -> d.get("_id")).containsExactly(EVENTO + ":" + ANA, EVENTO + ":" + BETO);
	}

	/** Parte 3: el mismo evento otra vez choca con el _id; no es error y no se cuenta. */
	@Test
	void unEventoRepetidoNoCuentaNiFalla() {
		when(mongo.insert(any(Document.class), eq(BitacoraDeAvisos.COLECCION))).thenThrow(new DuplicateKeyException("E11000"));
		BitacoraDeAvisos bitacora = new BitacoraDeAvisos(mongo, contactos);

		int nuevos = bitacora.registrar(realizada(ANA, BETO));

		assertThat(nuevos).isZero();
		verify(mongo, times(2)).insert(any(Document.class), eq(BitacoraDeAvisos.COLECCION));
	}

	@Test
	void unaRevertidaSoloAvisaAOrigen() {
		EventoTransferencia revertida = new EventoTransferencia(EVENTO, EventoTransferencia.REVERTIDA, 43L, ANA, BETO,
			new BigDecimal("50"), new BigDecimal("1000"), null, Instant.parse("2026-10-17T10:42:07Z"));

		List<BitacoraDeAvisos.Destinatario> destinatarios = BitacoraDeAvisos.destinatarios(revertida);

		assertThat(destinatarios).extracting(BitacoraDeAvisos.Destinatario::clabe).containsExactly(ANA);
	}
}
