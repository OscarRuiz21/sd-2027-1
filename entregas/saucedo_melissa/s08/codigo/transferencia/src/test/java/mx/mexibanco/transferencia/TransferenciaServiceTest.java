package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import mx.mexibanco.compartido.TransferenciaFallidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas del orquestador. Las tres primeras vienen del S07; las demas (S08) cubren la saga:
 * compensacion exitosa, compensacion fallida, cargo rechazado y fallo inyectado.
 */
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
                servicio = new TransferenciaService(transferencias, cuentas, movimientos, notificaciones, 0, false);
        }

        private EstadoTransferencia estadoFinalGuardado() {
                ArgumentCaptor<Transferencia> guardada = ArgumentCaptor.forClass(Transferencia.class);
                verify(transferencias, atLeastOnce()).save(guardada.capture());
                return guardada.getValue().getEstado();
        }

        @Test
        void transferirCargaAbonaRegistraDosAsientosYNotificaDos() {
                when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
                when(cuentas.abonar(DESTINO, MONTO)).thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));
                when(movimientos.registrar(any(), any(), any(), any(), any())).thenReturn(mock(MovimientoRemoto.class));
                when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> i.getArgument(0));

                Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

                assertThat(t.getMonto()).isEqualByComparingTo(MONTO);
                assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPLETADA);
                verify(movimientos).registrar(eq(ORIGEN), eq(TipoMovimiento.TRANSFERENCIA_ENVIADA), eq(MONTO.negate()), eq(new BigDecimal("800")), any());
                verify(movimientos).registrar(eq(DESTINO), eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA), eq(MONTO), eq(new BigDecimal("700")), any());
                verify(notificaciones, times(2)).notificar(any());
        }

        @Test
        void transferirAUnaCuentaQueNoExisteNoCobraNada() {
                when(cuentas.consultar(ORIGEN)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
                when(cuentas.consultar(DESTINO)).thenThrow(new ErrorRemotoException("cuenta", 404, "No existe la CLABE " + DESTINO));

                assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
                        .isInstanceOf(ErrorRemotoException.class)
                        .extracting("estado").isEqualTo(404);
                verify(cuentas, never()).cargar(any(), any());
                verifyNoInteractions(movimientos, transferencias, notificaciones);
        }

        @Test
        void transferirALaMismaCuentaSeRechaza() {
                assertThatThrownBy(() -> servicio.transferir(ORIGEN, ORIGEN, MONTO)).isInstanceOf(ReglaDeNegocioException.class);
                verifyNoInteractions(cuentas, movimientos, transferencias, notificaciones);
        }

        /** S08: si el abono falla, la saga devuelve el cargo con un deposito y queda COMPENSADA. */
        @Test
        void siElAbonoFallaSeCompensaConUnDepositoAlOrigen() {
                when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> i.getArgument(0));
                when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
                when(cuentas.abonar(DESTINO, MONTO)).thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"));
                when(cuentas.abonar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));

                assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
                        .isInstanceOfSatisfying(TransferenciaFallidaException.class,
                                e -> assertThat(e.getEstado()).isEqualTo("COMPENSADA"));

                verify(cuentas, times(1)).abonar(ORIGEN, MONTO); // la compensacion ocurre una sola vez
                verify(movimientos).registrar(eq(ORIGEN), eq(TipoMovimiento.DEPOSITO), eq(MONTO), eq(new BigDecimal("1000")), contains("compensacion"));
                verify(notificaciones, never()).notificar(any());
                assertThat(estadoFinalGuardado()).isEqualTo(EstadoTransferencia.COMPENSADA);
        }

        /** S08: si tambien falla la compensacion, el dinero queda retenido y se marca para intervencion. */
        @Test
        void siFallaElAbonoYLaCompensacionQuedaCompensacionPendiente() {
                when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> i.getArgument(0));
                when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
                when(cuentas.abonar(DESTINO, MONTO)).thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"));
                when(cuentas.abonar(ORIGEN, MONTO)).thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"));

                assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
                        .isInstanceOfSatisfying(TransferenciaFallidaException.class,
                                e -> assertThat(e.getEstado()).isEqualTo("COMPENSACION_PENDIENTE"));

                verifyNoInteractions(movimientos, notificaciones);
                assertThat(estadoFinalGuardado()).isEqualTo(EstadoTransferencia.COMPENSACION_PENDIENTE);
        }

        /** S08: si el cargo es rechazado no se movio dinero: FALLIDA, sin compensar, y el error original sale. */
        @Test
        void siElCargoEsRechazadoQuedaFallidaYNoSeCompensa() {
                when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> i.getArgument(0));
                when(cuentas.cargar(ORIGEN, MONTO)).thenThrow(new ErrorRemotoException("cuenta", 422, "Saldo insuficiente"));

                assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
                        .isInstanceOf(ErrorRemotoException.class)
                        .extracting("estado").isEqualTo(422);

                verify(cuentas, never()).abonar(any(), any());
                verifyNoInteractions(movimientos, notificaciones);
                assertThat(estadoFinalGuardado()).isEqualTo(EstadoTransferencia.FALLIDA);
        }

        /** S08: el interruptor de demo hace fallar el abono sin llamar al servicio; la compensacion si corre. */
        @Test
        void conFalloInyectadoElAbonoNoSeIntentaYSeCompensa() {
                servicio = new TransferenciaService(transferencias, cuentas, movimientos, notificaciones, 0, true);
                when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> i.getArgument(0));
                when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
                when(cuentas.abonar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));

                assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
                        .isInstanceOfSatisfying(TransferenciaFallidaException.class,
                                e -> assertThat(e.getEstado()).isEqualTo("COMPENSADA"));

                verify(cuentas, never()).abonar(DESTINO, MONTO);
                assertThat(estadoFinalGuardado()).isEqualTo(EstadoTransferencia.COMPENSADA);
        }
}
