package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import mx.mexibanco.notificacion.EventoTransferencia;
import mx.mexibanco.notificacion.Notificacion;
import mx.mexibanco.notificacion.NotificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** S09: que hace el consumidor con cada mensaje: ack, nack sin reencolar (DLQ) o nack reencolando. */
@ExtendWith(MockitoExtension.class)
class ConsumidorDeTransferenciasTest {

	private static final long ETIQUETA = 7L;
	private static final String EVENTO_VALIDO = """
		{"eventoId":"8a1f2c3e-5b7d-4e9a-9c0f-1d2e3f4a5b6c","tipo":"TransferenciaRealizada","transferenciaId":42,
		 "clabeOrigen":"002180000000000001","clabeDestino":"002180000000000002","monto":200.00,
		 "saldoOrigen":800.00,"saldoDestino":700.00,"fecha":"2026-10-17T10:42:07Z"}
		""";

	@Mock
	NotificacionService notificaciones;

	@Mock
	Channel canal;

	ConsumidorDeTransferencias consumidor;

	@BeforeEach
	void armar() {
		ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();   // como el de Spring Boot (fechas ISO)
		consumidor = new ConsumidorDeTransferencias(notificaciones, json, 0);
	}

	private static Message mensaje(String cuerpo) {
		MessageProperties propiedades = new MessageProperties();
		propiedades.setDeliveryTag(ETIQUETA);
		return new Message(cuerpo.getBytes(StandardCharsets.UTF_8), propiedades);
	}

	@Test
	void unEventoValidoSeGuardaYLuegoSeConfirma() throws Exception {
		when(notificaciones.avisarDeTransferencia(any())).thenReturn(List.of(mock(Notificacion.class), mock(Notificacion.class)));

		consumidor.recibir(mensaje(EVENTO_VALIDO), canal);

		ArgumentCaptor<EventoTransferencia> evento = ArgumentCaptor.forClass(EventoTransferencia.class);
		InOrder enOrden = inOrder(notificaciones, canal);
		enOrden.verify(notificaciones).avisarDeTransferencia(evento.capture());   // primero guardar...
		enOrden.verify(canal).basicAck(ETIQUETA, false);                          // ...luego el ack
		assertThat(evento.getValue().transferenciaId()).isEqualTo(42L);
		assertThat(evento.getValue().fecha()).hasToString("2026-10-17T10:42:07Z");
		verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
	}

	@Test
	void unJsonIlegibleVaALaDlqSinTocarLaBase() throws Exception {
		consumidor.recibir(mensaje("{\"hola\":"), canal);

		verify(canal).basicNack(ETIQUETA, false, false);   // requeue=false: al DLX
		verify(canal, never()).basicAck(anyLong(), anyBoolean());
		verifyNoInteractions(notificaciones);
	}

	@Test
	void unJsonBienFormadoSinEventoIdTambienEsVeneno() throws Exception {
		consumidor.recibir(mensaje("{\"tipo\":\"TransferenciaRealizada\",\"transferenciaId\":1}"), canal);

		verify(canal).basicNack(ETIQUETA, false, false);
		verifyNoInteractions(notificaciones);
	}

	@Test
	void unTipoDesconocidoTambienEsVeneno() throws Exception {
		consumidor.recibir(mensaje(EVENTO_VALIDO.replace("TransferenciaRealizada", "TransferenciaInventada")), canal);

		verify(canal).basicNack(ETIQUETA, false, false);
		verifyNoInteractions(notificaciones);
	}

	@Test
	void siFallaNuestraBaseElMensajeSeReencola() throws Exception {
		when(notificaciones.avisarDeTransferencia(any())).thenThrow(new RuntimeException("la base no contesta"));

		consumidor.recibir(mensaje(EVENTO_VALIDO), canal);

		verify(canal).basicNack(ETIQUETA, false, true);   // requeue=true: el mensaje esta bien
		verify(canal, never()).basicAck(anyLong(), anyBoolean());
	}

	@Test
	void unEventoRepetidoSeConfirmaAunqueNoGenereAvisos() throws Exception {
		when(notificaciones.avisarDeTransferencia(any())).thenReturn(List.of());

		consumidor.recibir(mensaje(EVENTO_VALIDO), canal);

		verify(canal).basicAck(ETIQUETA, false);
	}
}
