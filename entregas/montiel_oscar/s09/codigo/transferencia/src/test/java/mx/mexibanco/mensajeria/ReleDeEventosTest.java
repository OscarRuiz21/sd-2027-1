package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReleDeEventosTest {
	@Test
	@SuppressWarnings("unchecked")
	void conservaElEventoTrasFallaYLoMarcaAlConfirmarse() throws Exception {
		ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
		EventoTransferencia evento = EventoTransferencia.revertida(UUID.randomUUID(), 12L,
			"002180000000000001", "002180000000000002", new BigDecimal("200"),
			new BigDecimal("1000"), Instant.now());
		EventoOutbox fila = new EventoOutbox(evento.eventoId(), json.writeValueAsString(evento));
		EventoOutboxRepository outbox = mock(EventoOutboxRepository.class);
		PublicadorDeEventos publicador = mock(PublicadorDeEventos.class);
		TransactionTemplate transacciones = mock(TransactionTemplate.class);
		when(transacciones.execute(any())).thenAnswer(i ->
			((TransactionCallback<?>) i.getArgument(0)).doInTransaction(null));
		when(outbox.siguientePendiente()).thenReturn(Optional.of(fila), Optional.of(fila), Optional.empty());
		doThrow(new EventoNoPublicadoException("broker caido")).doNothing().when(publicador).publicar(evento);
		ReleDeEventos rele = new ReleDeEventos(outbox, publicador, json, transacciones);

		rele.publicarPendientes();
		assertThat(fila.getPublicadoEn()).isNull();
		rele.publicarPendientes();
		assertThat(fila.getPublicadoEn()).isNotNull();
		verify(publicador, times(2)).publicar(evento);
	}
}
