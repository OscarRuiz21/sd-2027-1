package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.AbonoRechazadoException;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
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

    private TransferenciaService servicio;
    private Transferencia guardada;

    @BeforeEach
    void armar() {
        RegistroSaga registro = new RegistroSaga(transferencias);

        servicio = new TransferenciaService(
                transferencias,
                registro,
                cuentas,
                movimientos,
                notificaciones,
                0);
    }

    private void prepararRegistro() {
        when(transferencias.saveAndFlush(any(Transferencia.class)))
                .thenAnswer(invocacion -> {
                    guardada = invocacion.getArgument(0);
                    if (guardada.getId() == null) {
                        ReflectionTestUtils.setField(guardada, "id", 1L);
                    }
                    return guardada;
                });

        when(transferencias.findById(1L))
                .thenAnswer(invocacion -> Optional.ofNullable(guardada));
    }

    private void prepararCargo() {
        when(cuentas.cargar(ORIGEN, MONTO))
                .thenReturn(new CuentaRemota(
                        ORIGEN, "Ana", new BigDecimal("800")));
    }

    private void provocarFallaControlada() {
        when(cuentas.abonarDestino(DESTINO, MONTO))
                .thenThrow(new AbonoRechazadoException(
                        "Falla de prueba antes de enviar el abono"));
    }

    @Test
    void transferenciaExitosaTerminaCompletada() {
        prepararRegistro();
        prepararCargo();

        when(cuentas.abonarDestino(DESTINO, MONTO))
                .thenReturn(new CuentaRemota(
                        DESTINO, "Beto", new BigDecimal("700")));

        when(movimientos.registrar(any(), any(), any(), any(), any()))
                .thenReturn(mock(MovimientoRemoto.class));

        Transferencia resultado =
                servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(resultado.getEstado())
                .isEqualTo(EstadoTransferencia.COMPLETADA);
        assertThat(resultado.getMonto()).isEqualByComparingTo(MONTO);

        verify(movimientos).registrar(
                eq(ORIGEN),
                eq(TipoMovimiento.TRANSFERENCIA_ENVIADA),
                eq(MONTO.negate()),
                eq(new BigDecimal("800")),
                anyString());

        verify(movimientos).registrar(
                eq(DESTINO),
                eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA),
                eq(MONTO),
                eq(new BigDecimal("700")),
                anyString());

        verify(notificaciones, times(2)).notificar(any());
        verify(cuentas, never()).abonar(ORIGEN, MONTO);
    }

    @Test
    void cuentaInexistenteFallaAntesDelCargo() {
        prepararRegistro();

        when(cuentas.consultar(ORIGEN))
                .thenReturn(new CuentaRemota(
                        ORIGEN, "Ana", new BigDecimal("1000")));

        when(cuentas.consultar(DESTINO))
                .thenThrow(new ErrorRemotoException(
                        "cuenta", 404, "No existe la cuenta destino"));

        Transferencia resultado =
                servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(resultado.getEstado())
                .isEqualTo(EstadoTransferencia.FALLIDA);

        assertThat(resultado.getDetalle())
                .contains("No existe la cuenta destino")
                .doesNotContain("Strict stubbing");

        verify(cuentas).consultar(ORIGEN);
        verify(cuentas).consultar(DESTINO);
        verify(cuentas, never()).cargar(anyString(), any());
        verify(cuentas, never()).abonarDestino(anyString(), any());
        verifyNoInteractions(movimientos, notificaciones);
    }

    @Test
    void mismaCuentaSeRechazaAntesDeCrearLaSaga() {
        assertThatThrownBy(() ->
                servicio.transferir(ORIGEN, ORIGEN, MONTO))
                .isInstanceOf(ReglaDeNegocioException.class);

        verifyNoInteractions(
                cuentas, movimientos, transferencias, notificaciones);
    }

    @Test
    void fallaControladaDevuelveElCargoYRegistraLaCompensacion() {
        prepararRegistro();
        prepararCargo();
        provocarFallaControlada();

        when(cuentas.abonar(ORIGEN, MONTO))
                .thenReturn(new CuentaRemota(
                        ORIGEN, "Ana", new BigDecimal("1000")));

        Transferencia resultado =
                servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(resultado.getEstado())
                .isEqualTo(EstadoTransferencia.COMPENSADA);

        var orden = inOrder(cuentas, movimientos);
        orden.verify(cuentas).cargar(ORIGEN, MONTO);
        orden.verify(cuentas).abonarDestino(DESTINO, MONTO);
        orden.verify(cuentas).abonar(ORIGEN, MONTO);
        orden.verify(movimientos).registrar(
                eq(ORIGEN),
                eq(TipoMovimiento.DEPOSITO),
                eq(MONTO),
                eq(new BigDecimal("1000")),
                contains("Compensación de transferencia 1"));

        verifyNoInteractions(notificaciones);
    }

    @Test
    void siLaDevolucionFallaQuedaCompensacionPendiente() {
        prepararRegistro();
        prepararCargo();
        provocarFallaControlada();

        when(cuentas.abonar(ORIGEN, MONTO))
                .thenThrow(new ResourceAccessException(
                        "No se pudo confirmar la devolución"));

        Transferencia resultado =
                servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(resultado.getEstado())
                .isEqualTo(EstadoTransferencia.COMPENSACION_PENDIENTE);

        verify(cuentas, times(1)).abonar(ORIGEN, MONTO);
        verifyNoInteractions(movimientos, notificaciones);
    }

    @Test
    void timeoutDelAbonoRequiereRevisionSinDevolverACiegas() {
        prepararRegistro();
        prepararCargo();

        when(cuentas.abonarDestino(DESTINO, MONTO))
                .thenThrow(new ResourceAccessException(
                        "Se agotó el tiempo de espera"));

        Transferencia resultado =
                servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(resultado.getEstado())
                .isEqualTo(EstadoTransferencia.REVISION_PENDIENTE);

        verify(cuentas, never()).abonar(ORIGEN, MONTO);
        verifyNoInteractions(movimientos, notificaciones);
    }

    @Test
    void siFallaElMovimientoDeDevolucionNoDevuelveDosVeces() {
        prepararRegistro();
        prepararCargo();
        provocarFallaControlada();

        when(cuentas.abonar(ORIGEN, MONTO))
                .thenReturn(new CuentaRemota(
                        ORIGEN, "Ana", new BigDecimal("1000")));

        when(movimientos.registrar(any(), any(), any(), any(), any()))
                .thenThrow(new ResourceAccessException(
                        "Movimiento no disponible"));

        Transferencia resultado =
                servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(resultado.getEstado())
                .isEqualTo(EstadoTransferencia.REVISION_PENDIENTE);
        assertThat(resultado.getDetalle())
                .contains("El dinero ya fue devuelto");

        verify(cuentas, times(1)).abonar(ORIGEN, MONTO);
    }
}