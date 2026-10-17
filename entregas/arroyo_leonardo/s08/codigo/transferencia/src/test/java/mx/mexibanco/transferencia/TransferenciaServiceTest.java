package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.*;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static mx.mexibanco.transferencia.EstadoTransferencia.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferenciaServiceTest {
    static final String O = "002180000000000201";
    static final String D = "002180000000000202";
    static final BigDecimal M = new BigDecimal("200");
    @Mock TransferenciaRepository repo;
    @Mock CuentaCliente cuentas;
    @Mock MovimientoCliente movimientos;
    @Mock NotificacionCliente notificaciones;
    TransferenciaService servicio;
    List<EstadoTransferencia> estados;

    @BeforeEach
    void preparar() {
        estados = new ArrayList<>();
        lenient().when(repo.saveAndFlush(any())).thenAnswer(i -> {
            Transferencia t = i.getArgument(0);
            estados.add(t.getEstado());
            return t;
        });
        servicio = servicio("");
    }

    TransferenciaService servicio(String falla) {
        return new TransferenciaService(repo, cuentas, movimientos, notificaciones, 0, falla);
    }

    void cargo() {
        when(cuentas.cargar(O, M)).thenReturn(new CuentaRemota(O, "Origen", new BigDecimal("800")));
    }

    @Test void exitoPersisteLosPasos() {
        cargo();
        when(cuentas.abonar(D, M)).thenReturn(new CuentaRemota(D, "Destino", new BigDecimal("700")));
        assertThat(servicio.transferir(O, D, M).getEstado()).isEqualTo(COMPLETADA);
        assertThat(estados).containsExactly(INICIADA, CARGO_EN_CURSO, CARGADA,
                ABONO_EN_CURSO, ABONADA, COMPLETADA);
        verify(movimientos, times(2)).registrar(any(), any(), any(), any(), any());
        verify(notificaciones, times(2)).notificar(any());
    }

    @Test void fallaSimuladaDevuelveYRegistra() {
        cargo();
        when(cuentas.abonar(O, M)).thenReturn(new CuentaRemota(O, "Origen", new BigDecimal("1000")));
        assertThat(servicio(D).transferir(O, D, M).getEstado()).isEqualTo(COMPENSADA);
        verify(cuentas, never()).abonar(D, M);
        verify(movimientos).registrar(eq(O), eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA),
                eq(M), eq(new BigDecimal("1000")), contains("COMPENSACION"));
    }

    @Test void devolucionFallidaQuedaPendiente() {
        cargo();
        when(cuentas.abonar(O, M)).thenThrow(new CircuitoAbiertoException("abierto"));
        assertThat(servicio(D).transferir(O, D, M).getEstado()).isEqualTo(COMPENSACION_PENDIENTE);
    }

    @Test void errorAntesDelCargoNoMueveDinero() {
        when(cuentas.consultar(O)).thenThrow(new ErrorRemotoException("cuenta", 404, "no existe"));
        assertThat(servicio.transferir(O, D, M).getEstado()).isEqualTo(FALLIDA);
        verify(cuentas, never()).cargar(any(), any());
        verifyNoInteractions(movimientos, notificaciones);
    }

    @Test void mismaCuentaSeRechazaSinCrearSaga() {
        assertThatThrownBy(() -> servicio.transferir(O, O, M)).isInstanceOf(ReglaDeNegocioException.class);
        verifyNoInteractions(repo, cuentas, movimientos, notificaciones);
    }

    @Test void abonoConRespuestaInciertaNoDevuelveACiegas() {
        cargo();
        when(cuentas.abonar(D, M)).thenThrow(new ErrorRemotoException("cuenta", 500, "incierto"));
        assertThat(servicio.transferir(O, D, M).getEstado()).isEqualTo(REVISION_PENDIENTE);
        verify(cuentas, never()).abonar(O, M);
    }

    @Test void fallaDeMovimientoDespuesDelAbonoNoDevuelve() {
        cargo();
        when(cuentas.abonar(D, M)).thenReturn(new CuentaRemota(D, "Destino", new BigDecimal("700")));
        when(movimientos.registrar(eq(D), any(), any(), any(), any()))
                .thenThrow(new ErrorRemotoException("movimiento", 503, "caído"));
        assertThat(servicio.transferir(O, D, M).getEstado()).isEqualTo(REGISTRO_PENDIENTE);
        verify(cuentas, never()).abonar(O, M);
    }

    @Test void fallaDelRegistroDeDevolucionNoRepiteElAbono() {
        cargo();
        when(cuentas.abonar(O, M)).thenReturn(new CuentaRemota(O, "Origen", new BigDecimal("1000")));
        when(movimientos.registrar(eq(O), eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA), any(), any(), any()))
                .thenThrow(new ErrorRemotoException("movimiento", 503, "caído"));
        Transferencia t = servicio(D).transferir(O, D, M);
        assertThat(t.getEstado()).isEqualTo(REGISTRO_PENDIENTE);
        assertThat(t.getDetalle()).contains("DINERO YA DEVUELTO");
        verify(cuentas, times(1)).abonar(O, M);
    }

    @Test void conexionRechazadaDuranteAbonoIntentaCompensar() {
        cargo();
        when(cuentas.abonar(D, M)).thenThrow(new org.springframework.web.client.ResourceAccessException(
                "conexión rechazada", new java.net.ConnectException("refused")));
        when(cuentas.abonar(O, M)).thenReturn(new CuentaRemota(O, "Origen", new BigDecimal("1000")));
        assertThat(servicio.transferir(O, D, M).getEstado()).isEqualTo(COMPENSADA);
        verify(cuentas).abonar(O, M);
    }

    @Test void notificacionFallidaNoDevuelveDinero() {
        cargo();
        when(cuentas.abonar(D, M)).thenReturn(new CuentaRemota(D, "Destino", new BigDecimal("700")));
        doThrow(new ErrorRemotoException("notificacion", 503, "caído"))
                .when(notificaciones).notificar(any());
        assertThat(servicio.transferir(O, D, M).getEstado()).isEqualTo(NOTIFICACION_PENDIENTE);
        verify(cuentas, never()).abonar(O, M);
    }
}
