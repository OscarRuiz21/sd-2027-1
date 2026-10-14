package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferenciaServiceTest {

    private static final String ORIGEN = "002180000000000001";
    private static final String DESTINO = "002180000000000002";
    private static final BigDecimal MONTO = new BigDecimal("200");

    @Mock
    TransferenciaRepository transferencias;

    @Mock
    CuentaCliente cuentas;

    @Mock
    MovimientoCliente movimientos;

    @Mock
    NotificacionCliente notificaciones;

    TransferenciaService servicio;

    @BeforeEach
    void armar() {
        servicio = new TransferenciaService(
                transferencias, cuentas, movimientos, notificaciones, 0, "");
    }

    private void prepararCuentas() {
        when(cuentas.consultar(ORIGEN))
                .thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
        when(cuentas.consultar(DESTINO))
                .thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("500")));
        when(transferencias.save(any(Transferencia.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void transferenciaExitosaTerminaCompletadaYRegistraDosMovimientos() {
        prepararCuentas();
        when(cuentas.cargar(ORIGEN, MONTO))
                .thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
        when(cuentas.abonar(DESTINO, MONTO))
                .thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));
        when(movimientos.registrar(any(), any(), any(), any(), any()))
                .thenReturn(mock(MovimientoRemoto.class));

        Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(t.getMonto()).isEqualByComparingTo(MONTO);
        assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPLETADA);
        verify(movimientos).registrar(
                eq(ORIGEN), eq(TipoMovimiento.TRANSFERENCIA_ENVIADA),
                eq(MONTO.negate()), eq(new BigDecimal("800")), any());
        verify(movimientos).registrar(
                eq(DESTINO), eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA),
                eq(MONTO), eq(new BigDecimal("700")), any());
        verify(notificaciones, times(2)).notificar(any());
    }

    @Test
    void cuentaDestinoInexistenteNoGeneraCargo() {
        when(cuentas.consultar(ORIGEN))
                .thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
        when(cuentas.consultar(DESTINO))
                .thenThrow(new ErrorRemotoException(
                        "cuenta", 404, "No existe la CLABE " + DESTINO));

        assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
                .isInstanceOf(ErrorRemotoException.class)
                .extracting("estado").isEqualTo(404);

        verify(cuentas, never()).cargar(any(), any());
        verifyNoInteractions(movimientos, transferencias, notificaciones);
    }

    @Test
    void transferenciaALaMismaCuentaSeRechaza() {
        assertThatThrownBy(() -> servicio.transferir(ORIGEN, ORIGEN, MONTO))
                .isInstanceOf(ReglaDeNegocioException.class);

        verifyNoInteractions(cuentas, movimientos, transferencias, notificaciones);
    }

    @Test
    void siElAbonoFallaSeCompensaElCargoYQuedaCompensada() {
        prepararCuentas();
        when(cuentas.cargar(ORIGEN, MONTO))
                .thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
        when(cuentas.abonar(DESTINO, MONTO))
                .thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"));

        Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPENSADA);
        assertThat(t.getMensaje()).contains("devuelto");
        verify(cuentas).compensar(eq(ORIGEN), eq(MONTO), anyString());
        verifyNoInteractions(movimientos, notificaciones);
    }

    @Test
    void siLaCompensacionFallaQuedaPendiente() {
        prepararCuentas();
        when(cuentas.cargar(ORIGEN, MONTO))
                .thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
        when(cuentas.abonar(DESTINO, MONTO))
                .thenThrow(new ErrorRemotoException("cuenta", 503, "abono fallido"));
        doThrow(new ErrorRemotoException("cuenta", 503, "cuenta no disponible"))
                .when(cuentas).compensar(eq(ORIGEN), eq(MONTO), anyString());

        Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPENSACION_PENDIENTE);
        assertThat(t.getMensaje()).contains("reintentar");
        verify(cuentas).compensar(eq(ORIGEN), eq(MONTO), anyString());
        verify(transferencias, atLeastOnce()).save(any(Transferencia.class));
    }
}
