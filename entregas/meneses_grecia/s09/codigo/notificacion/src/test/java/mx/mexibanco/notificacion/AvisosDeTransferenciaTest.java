package mx.mexibanco.notificacion;

import mx.mexibanco.notificacion.mensajeria.EventoProcesado;
import mx.mexibanco.notificacion.mensajeria.EventoProcesadoRepository;
import mx.mexibanco.notificacion.mensajeria.EventoTransferencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** LAB S09: cuantos avisos y para quien por cada evento, y que un duplicado no deja un segundo juego. */
@ExtendWith(MockitoExtension.class)
class AvisosDeTransferenciaTest {

	private static final String ORIGEN = "002180000000000001";
	private static final String DESTINO = "002180000000000002";

	@Mock
	NotificacionRepository notificaciones;

	@Mock
	EventoProcesadoRepository procesados;

	NotificacionService servicio;

	@BeforeEach
	void armar() {
		servicio = new NotificacionService(notificaciones, procesados);
	}

	private static EventoTransferencia evento(String tipo) {
		boolean realizada = EventoTransferencia.REALIZADA.equals(tipo);
		return new EventoTransferencia("evento-1", tipo, 42L, ORIGEN, DESTINO, new BigDecimal("200.00"),
			realizada ? new BigDecimal("800.00") : new BigDecimal("1000.00"), realizada ? new BigDecimal("700.00") : null,
			Instant.parse("2026-10-17T10:42:07Z"));
	}

	@SuppressWarnings("unchecked")
	private List<Notificacion> avisosGuardados() {
		ArgumentCaptor<List<Notificacion>> avisos = ArgumentCaptor.forClass(List.class);
		verify(notificaciones).saveAll(avisos.capture());
		return avisos.getValue();
	}

	@Test
	void transferenciaRealizadaDejaDosAvisosOrigenYDestino() {
		int guardados = servicio.registrarAvisos(evento(EventoTransferencia.REALIZADA), true);

		assertThat(guardados).isEqualTo(2);
		List<Notificacion> avisos = avisosGuardados();
		assertThat(avisos).extracting(Notificacion::getClabeCuenta).containsExactly(ORIGEN, DESTINO);
		assertThat(avisos.get(0).getMensaje()).contains("Enviaste $200.00", "saldo es $800.00");
		assertThat(avisos.get(1).getMensaje()).contains("Recibiste $200.00", "saldo es $700.00");
		assertThat(avisos).extracting(Notificacion::getEventoId).containsOnly("evento-1");
		verify(procesados).saveAndFlush(any(EventoProcesado.class));
	}

	@Test
	void transferenciaRevertidaDejaUnAvisoAOrigen() {
		int guardados = servicio.registrarAvisos(evento(EventoTransferencia.REVERTIDA), true);

		assertThat(guardados).isEqualTo(1);
		List<Notificacion> avisos = avisosGuardados();
		assertThat(avisos).extracting(Notificacion::getClabeCuenta).containsExactly(ORIGEN);
		assertThat(avisos.get(0).getMensaje()).contains("no se realizo y te devolvimos el dinero");
	}

	/** Parte 3: el mismo eventoId por segunda vez no guarda nada. */
	@Test
	void unEventoYaProcesadoNoDejaAvisos() {
		when(procesados.existsById("evento-1")).thenReturn(true);

		int guardados = servicio.registrarAvisos(evento(EventoTransferencia.REALIZADA), true);

		assertThat(guardados).isZero();
		verify(notificaciones, never()).saveAll(anyList());
		verify(procesados, never()).saveAndFlush(any());
	}

	/** Parte 3, "antes": sin idempotencia, cada copia deja su juego de avisos. */
	@Test
	void sinIdempotenciaCadaCopiaGuardaAvisos() {
		servicio.registrarAvisos(evento(EventoTransferencia.REALIZADA), false);
		servicio.registrarAvisos(evento(EventoTransferencia.REALIZADA), false);

		verify(notificaciones, times(2)).saveAll(anyList());
		verifyNoInteractions(procesados);
	}
}
