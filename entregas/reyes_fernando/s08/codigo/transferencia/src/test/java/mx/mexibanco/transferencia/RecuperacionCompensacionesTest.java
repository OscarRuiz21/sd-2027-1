package mx.mexibanco.transferencia;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RecuperacionCompensacionesTest {

	@Test
	void desactivadoNoConsultaLaBase() {
		TransferenciaRepository repositorio = mock(TransferenciaRepository.class);
		TransferenciaService servicio = mock(TransferenciaService.class);
		new RecuperacionCompensaciones(repositorio, servicio, false).revisar();
		verifyNoInteractions(repositorio, servicio);
	}

	@Test
	void activadoSoloRevisaLasFilasQueDevuelveLaBusquedaSegura() {
		TransferenciaRepository repositorio = mock(TransferenciaRepository.class);
		TransferenciaService servicio = mock(TransferenciaService.class);
		Transferencia pendiente = mock(Transferencia.class);
		when(pendiente.getId()).thenReturn(12L);
		when(repositorio.findByEstadoInAndLlaveCompensacionIsNotNullAndProximoReintentoLessThanEqualOrderByProximoReintentoAsc(
			any(), any(Instant.class))).thenReturn(List.of(pendiente));

		new RecuperacionCompensaciones(repositorio, servicio, true).revisar();

		verify(servicio).reintentarCompensacion(12L);
		verify(repositorio).findByEstadoInAndLlaveCompensacionIsNotNullAndProximoReintentoLessThanEqualOrderByProximoReintentoAsc(
			eq(List.of(EstadoTransferencia.COMPENSACION_PENDIENTE, EstadoTransferencia.COMPENSANDO)), any(Instant.class));
	}
}
