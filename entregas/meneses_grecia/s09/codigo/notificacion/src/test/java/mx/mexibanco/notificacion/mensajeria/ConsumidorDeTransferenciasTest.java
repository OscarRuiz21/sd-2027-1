package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.rabbitmq.client.Channel;
import mx.mexibanco.notificacion.NotificacionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * LAB S09: lo que hace el consumidor con cada mensaje: ack despues de guardar, ack sin guardar si es
 * duplicado, nack sin reencolar (a la DLQ) si es ilegible y nack reencolando si falla la base.
 */
@ExtendWith(MockitoExtension.class)
class ConsumidorDeTransferenciasTest {

	private static final long ETIQUETA = 7L;
	private static final String EVENTO = """
		{"eventoId":"8a1f2c3e-5b7d-4e9a-9c0f-1d2e3f4a5b6c","tipo":"TransferenciaRealizada","transferenciaId":42,
		 "clabeOrigen":"002180000000000001","clabeDestino":"002180000000000002","monto":200.00,
		 "saldoOrigen":800.00,"saldoDestino":700.00,"fecha":"2026-10-17T10:42:07Z"}""";

	@Mock
	NotificacionService notificaciones;

	@Mock
	Channel canal;

	ObjectMapper json = JsonMapper.builder().findAndAddModules().build();

	private ConsumidorDeTransferencias consumidor(boolean reencolarIlegibles) {
		return new ConsumidorDeTransferencias(notificaciones, json, true, 0, reencolarIlegibles);
	}

	private static Message mensaje(String cuerpo) {
		MessageProperties propiedades = new MessageProperties();
		propiedades.setDeliveryTag(ETIQUETA);
		propiedades.setReceivedRoutingKey("transferencia.realizada");
		return new Message(cuerpo.getBytes(StandardCharsets.UTF_8), propiedades);
	}

	@Test
	void guardaLosAvisosYDespuesHaceAck() throws Exception {
		when(notificaciones.registrarAvisos(any(), eq(true))).thenReturn(2);

		consumidor(false).recibir(mensaje(EVENTO), canal);

		var enOrden = inOrder(notificaciones, canal);
		enOrden.verify(notificaciones).registrarAvisos(any(EventoTransferencia.class), eq(true));
		enOrden.verify(canal).basicAck(ETIQUETA, false);
		verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
	}

	@Test
	void unDuplicadoSeConfirmaSinGuardarOtraVez() throws Exception {
		when(notificaciones.registrarAvisos(any(), eq(true))).thenReturn(0);

		consumidor(false).recibir(mensaje(EVENTO), canal);

		verify(canal).basicAck(ETIQUETA, false);
	}

	@Test
	void dosCopiasALaVezTambienSeConfirman() throws Exception {
		when(notificaciones.registrarAvisos(any(), eq(true))).thenThrow(new DataIntegrityViolationException("llave duplicada"));

		consumidor(false).recibir(mensaje(EVENTO), canal);

		verify(canal).basicAck(ETIQUETA, false);
	}

	/** Parte 4: {"hola":  no es un evento. Se rechaza SIN reencolar: RabbitMQ lo manda a la DLQ. */
	@Test
	void unMensajeEnvenenadoVaALaDlq() throws Exception {
		consumidor(false).recibir(mensaje("{\"hola\": "), canal);

		verify(canal).basicNack(ETIQUETA, false, false);
		verify(canal, never()).basicAck(anyLong(), anyBoolean());
		verifyNoInteractions(notificaciones);
	}

	@Test
	void unJsonValidoQueNoEsEventoTambienVaALaDlq() throws Exception {
		consumidor(false).recibir(mensaje("{\"hola\": 1}"), canal);

		verify(canal).basicNack(ETIQUETA, false, false);
		verifyNoInteractions(notificaciones);
	}

	@Test
	void conReencolarIlegiblesElVenenoRebota() throws Exception {
		consumidor(true).recibir(mensaje("{\"hola\": "), canal);

		verify(canal).basicNack(ETIQUETA, false, true);
	}

	@Test
	void siFallaLaBaseSeReencolaParaReintentar() throws Exception {
		when(notificaciones.registrarAvisos(any(), eq(true))).thenThrow(new IllegalStateException("base caida"));

		consumidor(false).recibir(mensaje(EVENTO), canal);

		verify(canal).basicNack(ETIQUETA, false, true);
		verify(canal, never()).basicAck(anyLong(), anyBoolean());
	}
}
