package mx.mexibanco.notificacion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Nueva en v06a: el mensaje del aviso se arma igual que en el monolito, ahora desde un DTO. */
@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

	@Mock
	NotificacionRepository notificaciones;

	@Mock
	EventoProcesadoRepository procesados;

	@InjectMocks
	NotificacionService servicio;

	@Test
	void elAvisoDiceTipoMontoSinSignoYSaldo() {
		when(notificaciones.save(any(Notificacion.class))).thenAnswer(i -> i.getArgument(0));

		Notificacion aviso = servicio.notificar(new AvisoDeMovimiento("002180000000000001",
			"TRANSFERENCIA_ENVIADA", new BigDecimal("-200"), new BigDecimal("800")));

		assertThat(aviso.getClabeCuenta()).isEqualTo("002180000000000001");
		assertThat(aviso.getMensaje()).isEqualTo("TRANSFERENCIA_ENVIADA por 200, saldo resultante 800");
	}

	private static final String ANA = "002180000000000001";
	private static final String BETO = "002180000000000002";

	private static EventoTransferencia realizada(UUID id) {
		return new EventoTransferencia(id, EventoTransferencia.REALIZADA, 42L, ANA, BETO,
			new BigDecimal("200"), new BigDecimal("800"), new BigDecimal("700"), Instant.now());
	}

	/** S09: una TransferenciaRealizada deja dos avisos, uno por cada lado. */
	@Test
	void unaTransferenciaRealizadaAvisaAOrigenYADestino() {
		UUID id = UUID.randomUUID();
		when(procesados.marcarSiEsNuevo(id, EventoTransferencia.REALIZADA, 42L)).thenReturn(1);
		when(notificaciones.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

		List<Notificacion> avisos = servicio.avisarDeTransferencia(realizada(id));

		assertThat(avisos).extracting(Notificacion::getClabeCuenta).containsExactly(ANA, BETO);
		assertThat(avisos).extracting(Notificacion::getMensaje).containsExactly(
			"TRANSFERENCIA_ENVIADA por 200, saldo resultante 800",
			"TRANSFERENCIA_RECIBIDA por 200, saldo resultante 700");
	}

	/** S09: una TransferenciaRevertida deja un solo aviso, a origen. */
	@Test
	void unaTransferenciaRevertidaAvisaSoloAOrigen() {
		UUID id = UUID.randomUUID();
		EventoTransferencia revertida = new EventoTransferencia(id, EventoTransferencia.REVERTIDA, 43L, ANA, BETO,
			new BigDecimal("150"), new BigDecimal("1000"), null, Instant.now());
		when(procesados.marcarSiEsNuevo(id, EventoTransferencia.REVERTIDA, 43L)).thenReturn(1);
		when(notificaciones.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

		List<Notificacion> avisos = servicio.avisarDeTransferencia(revertida);

		assertThat(avisos).singleElement().satisfies(a -> {
			assertThat(a.getClabeCuenta()).isEqualTo(ANA);
			assertThat(a.getMensaje()).isEqualTo("DEVOLUCION_TRANSFERENCIA por 150, saldo resultante 1000");
		});
	}

	/** S09, tarea del duplicado: si el eventoId ya estaba marcado, no se guarda ningun aviso. */
	@Test
	void unEventoRepetidoNoGuardaAvisos() {
		UUID id = UUID.randomUUID();
		when(procesados.marcarSiEsNuevo(id, EventoTransferencia.REALIZADA, 42L)).thenReturn(0);

		List<Notificacion> avisos = servicio.avisarDeTransferencia(realizada(id));

		assertThat(avisos).isEmpty();
		verify(notificaciones, never()).saveAll(anyList());
	}
}
