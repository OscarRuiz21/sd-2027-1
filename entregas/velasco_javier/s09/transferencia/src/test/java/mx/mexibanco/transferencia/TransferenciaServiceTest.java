
package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.mensajeria.PublicadorDeEventos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static mx.mexibanco.transferencia.EstadoTransferencia.*;
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
    private static final long ID = 12L;

    @Mock
    TransferenciaRepository transferencias;

    @Mock
    CuentaCliente cuentas;

    @Mock
    mx.mexibanco.clientes.MovimientoCliente movimientos;

    @Mock
    PublicadorDeEventos eventos;

    TransferenciaService servicio;

    /** El estado de la fila en cada save(), en orden. */
    List<EstadoTransferencia> estadosGuardados = new ArrayList<>();

    @BeforeEach
    void armar() {
        servicio = nuevoServicio("");
    }

    private TransferenciaService nuevoServicio(String fallarAbonoAClabe) {
        return new TransferenciaService(
                transferencias, cuentas, movimientos, eventos, 0, fallarAbonoAClabe);
    }

    /** Como la base: al primer save le pone id, y en cada save anota el estado. */
    private void repositorioQueAnota() {
        when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> {
            Transferencia t = i.getArgument(0);
            if (t.getId() == null) {
                ReflectionTestUtils.setField(t, "id", ID);
            }
            estadosGuardados.add(t.getEstado());
            return t;
        });
    }

    private void cargoExitoso() {
        when(cuentas.cargar(ORIGEN, MONTO, "transferencia-12-cargo"))
                .thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
    }

    private void ledgerQueAcepta() {
        when(movimientos.registrar(anyString(), any(), any(), any(), any(), any()))
                .thenReturn(mock(mx.mexibanco.clientes.MovimientoRemoto.class));
    }

    private Transferencia pendienteDeCompensar() {
        Transferencia pendiente = new Transferencia(ORIGEN, DESTINO, MONTO);
        ReflectionTestUtils.setField(pendiente, "id", ID);
        pendiente.marcarCargada(new BigDecimal("800"));
        pendiente.marcarCompensacionPendiente("cuenta no contesto");
        return pendiente;
    }

    @Test
    void elCaminoFelizGuardaCadaPasoYTerminaCompletada() {
        repositorioQueAnota();
        cargoExitoso();
        when(cuentas.abonar(DESTINO, MONTO, "transferencia-12-abono"))
                .thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));
        ledgerQueAcepta();

        Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(t.getEstado()).isEqualTo(COMPLETADA);
        assertThat(estadosGuardados).containsExactly(INICIADA, CARGADA, ABONADA, COMPLETADA);

        verify(movimientos).registrar(
                eq("transferencia-12-asiento-cargo"),
                eq(ORIGEN),
                eq(TipoMovimiento.TRANSFERENCIA_ENVIADA),
                eq(MONTO.negate()),
                eq(new BigDecimal("800")),
                any());

        verify(movimientos).registrar(
                eq("transferencia-12-asiento-abono"),
                eq(DESTINO),
                eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA),
                eq(MONTO),
                eq(new BigDecimal("700")),
                any());

        verify(eventos).publicar(any());
        verify(cuentas, never()).abonar(eq(ORIGEN), any(), any());
    }

    @Test
    void transferirAUnaCuentaQueNoExisteNoCobraNada() {
        when(cuentas.consultar(ORIGEN))
                .thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
        when(cuentas.consultar(DESTINO))
                .thenThrow(new ErrorRemotoException(
                        "cuenta", 404, "No existe la CLABE " + DESTINO));

        assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
                .isInstanceOf(ErrorRemotoException.class)
                .extracting("estado").isEqualTo(404);

        verify(cuentas, never()).cargar(any(), any(), any());
        verifyNoInteractions(movimientos, transferencias, eventos);
    }

    @Test
    void transferirALaMismaCuentaSeRechaza() {
        assertThatThrownBy(() -> servicio.transferir(ORIGEN, ORIGEN, MONTO))
                .isInstanceOf(mx.mexibanco.compartido.ReglaDeNegocioException.class);

        verifyNoInteractions(cuentas, movimientos, transferencias, eventos);
    }

    @Test
    void siElCargoSeRechazaQuedaFallidaSinCompensarNada() {
        repositorioQueAnota();
        when(cuentas.cargar(ORIGEN, MONTO, "transferencia-12-cargo"))
                .thenThrow(new ErrorRemotoException(
                        "cuenta", 422, "Saldo insuficiente en la CLABE " + ORIGEN));

        assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
                .isInstanceOf(ErrorRemotoException.class)
                .extracting("estado").isEqualTo(422);

        assertThat(estadosGuardados).containsExactly(INICIADA, FALLIDA);
        verify(cuentas, never()).abonar(any(), any(), any());
        verifyNoInteractions(movimientos, eventos);
    }

    @Test
    void siElAbonoFallaSeDevuelveElCargoYQuedaCompensada() {
        repositorioQueAnota();
        cargoExitoso();

        when(cuentas.abonar(DESTINO, MONTO, "transferencia-12-abono"))
                .thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"));

        when(cuentas.abonar(ORIGEN, MONTO, "transferencia-12-devolucion"))
                .thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));

        ledgerQueAcepta();

        Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(t.getEstado()).isEqualTo(COMPENSADA);
        assertThat(t.getMotivo()).contains("cuenta no contesto");
        assertThat(estadosGuardados).containsExactly(INICIADA, CARGADA, COMPENSADA);

        InOrder enOrden = inOrder(movimientos);

        enOrden.verify(movimientos).registrar(
                eq("transferencia-12-asiento-cargo"),
                eq(ORIGEN),
                eq(TipoMovimiento.TRANSFERENCIA_ENVIADA),
                eq(MONTO.negate()),
                eq(new BigDecimal("800")),
                any());

        enOrden.verify(movimientos).registrar(
                eq("transferencia-12-asiento-devolucion"),
                eq(ORIGEN),
                eq(TipoMovimiento.DEVOLUCION_TRANSFERENCIA),
                eq(MONTO),
                eq(new BigDecimal("1000")),
                any());

        verify(eventos).publicar(any());
    }

    @Test
    void laFallaProvocadaDelAbonoNoLlamaACuentaYSeCompensa() {
        servicio = nuevoServicio(DESTINO);
        repositorioQueAnota();
        cargoExitoso();

        when(cuentas.abonar(ORIGEN, MONTO, "transferencia-12-devolucion"))
                .thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));

        ledgerQueAcepta();

        Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(t.getEstado()).isEqualTo(COMPENSADA);
        assertThat(t.getMotivo()).contains("FALLAR_ABONO_A_CLABE");

        verify(cuentas, never()).abonar(eq(DESTINO), any(), any());
        verify(eventos).publicar(any());
    }

    @Test
    void siTambienFallaLaDevolucionQuedaCompensacionPendiente() {
        repositorioQueAnota();
        cargoExitoso();

        when(cuentas.abonar(any(), any(), any()))
                .thenThrow(new ResourceAccessException("Connection refused"));

        Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(t.getEstado()).isEqualTo(COMPENSACION_PENDIENTE);
        assertThat(t.getIntentosDeCompensacion()).isEqualTo(1);
        assertThat(estadosGuardados)
                .containsExactly(INICIADA, CARGADA, COMPENSACION_PENDIENTE);

        verify(cuentas).abonar(ORIGEN, MONTO, "transferencia-12-devolucion");
        verifyNoInteractions(eventos);
    }

    @Test
    void elReintentoTerminaLaCompensacionPendienteConLaMismaClave() {
        Transferencia pendiente = pendienteDeCompensar();

        when(transferencias.findByEstado(COMPENSACION_PENDIENTE))
                .thenReturn(List.of(pendiente));

        repositorioQueAnota();

        when(cuentas.abonar(ORIGEN, MONTO, "transferencia-12-devolucion"))
                .thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));

        ledgerQueAcepta();

        int terminadas = servicio.reintentarCompensacionesPendientes();

        assertThat(terminadas).isEqualTo(1);
        assertThat(pendiente.getEstado()).isEqualTo(COMPENSADA);
        assertThat(pendiente.getIntentosDeCompensacion()).isEqualTo(2);

        verify(cuentas).abonar(ORIGEN, MONTO, "transferencia-12-devolucion");
        verify(eventos).publicar(any());
    }

    @Test
    void unReintentoQueVuelveAFallarSigueEnPendienteYCuentaElIntento() {
        Transferencia pendiente = pendienteDeCompensar();

        when(transferencias.findByEstado(COMPENSACION_PENDIENTE))
                .thenReturn(List.of(pendiente));

        repositorioQueAnota();

        when(cuentas.abonar(any(), any(), any()))
                .thenThrow(new ResourceAccessException("Connection refused"));

        int terminadas = servicio.reintentarCompensacionesPendientes();

        assertThat(terminadas).isZero();
        assertThat(pendiente.getEstado()).isEqualTo(COMPENSACION_PENDIENTE);
        assertThat(pendiente.getIntentosDeCompensacion()).isEqualTo(2);

        verifyNoInteractions(eventos);
    }

    @Test
    void siFallaElLedgerDespuesDelAbonoNoSeDevuelveNadaYQuedaAbonada() {
        repositorioQueAnota();
        cargoExitoso();

        when(cuentas.abonar(DESTINO, MONTO, "transferencia-12-abono"))
                .thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));

        when(movimientos.registrar(anyString(), any(), any(), any(), any(), any()))
                .thenThrow(new ResourceAccessException("movimiento caido"));

        assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
                .isInstanceOf(ResourceAccessException.class);

        assertThat(estadosGuardados).containsExactly(INICIADA, CARGADA, ABONADA);
        verify(cuentas, never()).abonar(eq(ORIGEN), any(), any());
        verifyNoInteractions(eventos);
    }
}
