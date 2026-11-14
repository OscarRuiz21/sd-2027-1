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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * S08: ademas de las pruebas de la S07, se documenta la SAGA:
 *  - el camino feliz queda en COMPLETADA,
 *  - si el cargo falla, la transferencia queda en FALLIDA (no se movio dinero),
 *  - si el abono falla, se compensa (COMPENSADA),
 *  - si la compensacion tambien falla, queda en COMPENSACION_PENDIENTE.
 */
@ExtendWith(MockitoExtension.class)
class TransferenciaServiceTest {

    private static final String ORIGEN = "002180000000000001";
    private static final String DESTINO = "002180000000000002";
    private static final BigDecimal MONTO = new BigDecimal("200");

    @Mock TransferenciaRepository transferencias;
    @Mock CuentaCliente cuentas;
    @Mock MovimientoCliente movimientos;
    @Mock NotificacionCliente notificaciones;
    @Mock CircuitBreakerCuenta breaker;

    TransferenciaService servicio;

    @BeforeEach
    void armar() {
        servicio = new TransferenciaService(transferencias, cuentas, movimientos, notificaciones,
                breaker, 0L, "");

        // El breaker del test no corta: solo ejecuta el Supplier tal cual.
        lenient().when(breaker.ejecutar(any())).thenAnswer(inv -> {
            Supplier<?> s = inv.getArgument(0);
            return s.get();
        });

        lenient().when(transferencias.save(any(Transferencia.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void transferirCargaAbonaRegistraDosAsientosYNotificaDos() {
        when(cuentas.cargar(eq(ORIGEN), eq(MONTO), anyString()))
                .thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
        when(cuentas.abonar(eq(DESTINO), eq(MONTO), anyString()))
                .thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));
        when(movimientos.registrar(any(), any(), any(), any(), any())).thenReturn(mock(MovimientoRemoto.class));

        Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPLETADA);
        assertThat(t.getMonto()).isEqualByComparingTo(MONTO);
        verify(movimientos).registrar(eq(ORIGEN), eq(TipoMovimiento.TRANSFERENCIA_ENVIADA), eq(MONTO.negate()), eq(new BigDecimal("800")), any());
        verify(movimientos).registrar(eq(DESTINO), eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA), eq(MONTO), eq(new BigDecimal("700")), any());
        verify(notificaciones, times(2)).notificar(any());
    }

    @Test
    void transferirALaMismaCuentaSeRechaza() {
        assertThatThrownBy(() -> servicio.transferir(ORIGEN, ORIGEN, MONTO)).isInstanceOf(ReglaDeNegocioException.class);
        verifyNoInteractions(cuentas, movimientos, transferencias, notificaciones);
    }

    @Test
    void siElCargoFallaLaTransferenciaQuedaFallida() {
        when(cuentas.cargar(eq(ORIGEN), eq(MONTO), anyString()))
                .thenThrow(new ErrorRemotoException("cuenta", 422, "Saldo insuficiente"));

        Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.FALLIDA);
        verify(cuentas, never()).abonar(any(), any(), any());
    }

    @Test
    void siElAbonoFallaSeCompensaElCargo() {
        when(cuentas.cargar(eq(ORIGEN), eq(MONTO), anyString()))
                .thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
        when(cuentas.abonar(eq(DESTINO), eq(MONTO), anyString()))
                .thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"));
        // La compensacion abona al ORIGEN
        when(cuentas.abonar(eq(ORIGEN), eq(MONTO), anyString()))
                .thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));

        Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPENSADA);
        verify(cuentas).cargar(eq(ORIGEN), eq(MONTO), anyString());
        verify(cuentas).abonar(eq(ORIGEN), eq(MONTO), anyString());   // la compensacion
    }

    @Test
    void siLaCompensacionFallaQuedaCompensacionPendiente() {
        when(cuentas.cargar(eq(ORIGEN), eq(MONTO), anyString()))
                .thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
        when(cuentas.abonar(eq(DESTINO), eq(MONTO), anyString()))
                .thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"));
        when(cuentas.abonar(eq(ORIGEN), eq(MONTO), anyString()))
                .thenThrow(new ErrorRemotoException("cuenta", 503, "compensacion tampoco"));

        Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPENSACION_PENDIENTE);
    }
}
