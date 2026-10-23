package mx.mexibanco.mensajeria;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicadorDeEventosTest {
	private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
	private final PublicadorDeEventos publicador = new PublicadorDeEventos(rabbit, 50);
	private final EventoTransferencia evento = EventoTransferencia.realizada(UUID.randomUUID(), 12L,
		"002180000000000001", "002180000000000002", new BigDecimal("200"),
		new BigDecimal("800"), new BigDecimal("700"), Instant.now());

	private CorrelationData correlacionEnviada() {
		ArgumentCaptor<CorrelationData> captor = ArgumentCaptor.forClass(CorrelationData.class);
		verify(rabbit).convertAndSend(eq("transferencias"), eq("transferencia.realizada"), eq(evento),
			any(MessagePostProcessor.class), captor.capture());
		return captor.getValue();
	}

	@Test
	void confirmaUnMensajePersistenteConIdEstable() {
		doAnswer(invocacion -> {
			CorrelationData correlacion = invocacion.getArgument(4);
			correlacion.getFuture().complete(new CorrelationData.Confirm(true, null));
			return null;
		}).when(rabbit).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class), any(CorrelationData.class));

		publicador.publicar(evento);
		assertThat(correlacionEnviada().getId()).isEqualTo(evento.eventoId().toString());

		ArgumentCaptor<MessagePostProcessor> procesador = ArgumentCaptor.forClass(MessagePostProcessor.class);
		verify(rabbit).convertAndSend(anyString(), anyString(), any(), procesador.capture(), any(CorrelationData.class));
		var mensaje = new org.springframework.amqp.core.Message(new byte[0]);
		procesador.getValue().postProcessMessage(mensaje);
		assertThat(mensaje.getMessageProperties().getMessageId()).isEqualTo(evento.eventoId().toString());
		assertThat(mensaje.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
	}

	@Test
	void unNackNoCuentaComoPublicado() {
		doAnswer(invocacion -> {
			CorrelationData correlacion = invocacion.getArgument(4);
			correlacion.getFuture().complete(new CorrelationData.Confirm(false, "rechazado"));
			return null;
		}).when(rabbit).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class), any(CorrelationData.class));

		assertThatThrownBy(() -> publicador.publicar(evento)).isInstanceOf(EventoNoPublicadoException.class)
			.hasMessageContaining("rechazado");
	}

	@Test
	void unMensajeSinColaNoCuentaComoPublicado() {
		doAnswer(invocacion -> {
			CorrelationData correlacion = invocacion.getArgument(4);
			correlacion.setReturned(new ReturnedMessage(new org.springframework.amqp.core.Message(new byte[0]),
				312, "NO_ROUTE", "transferencias", "transferencia.realizada"));
			correlacion.getFuture().complete(new CorrelationData.Confirm(true, null));
			return null;
		}).when(rabbit).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class), any(CorrelationData.class));

		assertThatThrownBy(() -> publicador.publicar(evento)).isInstanceOf(EventoNoPublicadoException.class)
			.hasMessageContaining("Ninguna cola");
	}
}
