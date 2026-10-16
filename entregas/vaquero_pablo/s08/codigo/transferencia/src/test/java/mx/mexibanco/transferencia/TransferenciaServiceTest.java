package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.*;
import mx.mexibanco.compartido.*;
import mx.mexibanco.resiliencia.CircuitoAbiertoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import static mx.mexibanco.transferencia.EstadoTransferencia.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferenciaServiceTest {
    private static final String A = "002180000000000001", B = "002180000000000002";
    private static final BigDecimal M = new BigDecimal("200.00");
    @Mock TransferenciaRepository repo;
    @Mock CuentaCliente cuentas;
    @Mock MovimientoCliente movimientos;
    @Mock NotificacionCliente avisos;
    TransferenciaService servicio;
    List<EstadoTransferencia> guardados;

    @BeforeEach void preparar() {
        servicio = new TransferenciaService(repo, cuentas, movimientos, avisos, 0);
        guardados = new ArrayList<>();
        lenient().when(repo.saveAndFlush(any())).thenAnswer(i -> {
            Transferencia t = i.getArgument(0);
            guardados.add(t.getEstado());
            return t;
        });
    }
    void cargo() {
        when(cuentas.cargar(A,M)).thenReturn(new CuentaRemota(A,"Origen",new BigDecimal("800")));
    }
    void exito() {
        cargo();
        when(cuentas.abonar(B,M)).thenReturn(new CuentaRemota(B,"Destino",new BigDecimal("700")));
    }
    void rechazarAbono() {
        cargo();
        when(cuentas.abonar(B,M)).thenThrow(new ErrorRemotoException("cuenta",422,"abono rechazado"));
    }

    @Test void exitoGuardaCadaPasoYRegistraLosDosLados() {
        exito();
        Transferencia t = servicio.transferir(A,B,M);
        assertThat(t.getEstado()).isEqualTo(COMPLETADA);
        assertThat(guardados).containsExactly(INICIADA,CARGO_EN_PROCESO,CARGADA,ABONO_EN_PROCESO,ABONADA,COMPLETADA);
        verify(movimientos).registrar(eq(A),eq(TipoMovimiento.TRANSFERENCIA_ENVIADA),eq(M.negate()),eq(new BigDecimal("800")),any());
        verify(movimientos).registrar(eq(B),eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA),eq(M),eq(new BigDecimal("700")),any());
        verify(avisos,times(2)).notificar(any());
    }
    @Test void rechazoDeAbonoDevuelveUnaSolaVezYDejaMovimientoDeCompensacion() {
        rechazarAbono();
        when(cuentas.abonar(A,M)).thenReturn(new CuentaRemota(A,"Origen",new BigDecimal("1000")));
        assertThat(servicio.transferir(A,B,M).getEstado()).isEqualTo(COMPENSADA);
        verify(cuentas,times(1)).abonar(A,M);
        verify(movimientos).registrar(eq(A),eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA),eq(M),eq(new BigDecimal("1000")),contains("COMPENSACION"));
        assertThat(guardados).containsSubsequence(CARGADA,COMPENSACION_EN_PROCESO,COMPENSACION_APLICADA,COMPENSADA);
        verifyNoInteractions(avisos);
    }
    @Test void cuentaApagadaDejaCompensacionPendiente() {
        cargo();
        when(cuentas.abonar(anyString(),eq(M))).thenThrow(new ResourceAccessException("Conexion rechazada",new ConnectException()));
        assertThat(servicio.transferir(A,B,M).getEstado()).isEqualTo(COMPENSACION_PENDIENTE);
        verify(cuentas).abonar(A,M);
        verifyNoInteractions(movimientos,avisos);
    }
    @Test void circuitoAbiertoDuranteCompensacionDejaPendiente() {
        rechazarAbono();
        when(cuentas.abonar(A,M)).thenThrow(new CircuitoAbiertoException());
        assertThat(servicio.transferir(A,B,M).getEstado()).isEqualTo(COMPENSACION_PENDIENTE);
    }
    @Test void timeoutDeAbonoNoDevuelvePorqueDestinoPudoHaberCobrado() {
        cargo();
        when(cuentas.abonar(B,M)).thenThrow(new ResourceAccessException("timeout",new SocketTimeoutException()));
        assertThat(servicio.transferir(A,B,M).getEstado()).isEqualTo(REVISION_PENDIENTE);
        verify(cuentas,never()).abonar(eq(A),any());
        verifyNoInteractions(movimientos,avisos);
    }
    @Test void un500DeAbonoTampocoDemuestraQueNoSeAplico() {
        cargo();
        when(cuentas.abonar(B,M)).thenThrow(new ErrorRemotoException("cuenta",500,"error despues de commit"));
        assertThat(servicio.transferir(A,B,M).getEstado()).isEqualTo(REVISION_PENDIENTE);
        verify(cuentas,never()).abonar(eq(A),any());
    }
    @Test void timeoutDeCargoNoRepiteNiCompensaACiegas() {
        when(cuentas.cargar(A,M)).thenThrow(new ResourceAccessException("timeout",new SocketTimeoutException()));
        assertThat(servicio.transferir(A,B,M).getEstado()).isEqualTo(REVISION_PENDIENTE);
        verify(cuentas,never()).abonar(any(),any());
    }
    @Test void timeoutDeCompensacionRequiereRevisionSinReintento() {
        rechazarAbono();
        when(cuentas.abonar(A,M)).thenThrow(new ResourceAccessException("timeout",new SocketTimeoutException()));
        assertThat(servicio.transferir(A,B,M).getEstado()).isEqualTo(REVISION_PENDIENTE);
        verify(cuentas,times(1)).abonar(A,M);
    }
    @Test void faltaDeCuentaNoMueveDineroYDejaRechazoGuardado() {
        when(cuentas.consultar(A)).thenReturn(new CuentaRemota(A,"Origen",new BigDecimal("1000")));
        when(cuentas.consultar(B)).thenThrow(new ErrorRemotoException("cuenta",404,"no existe"));
        assertThat(servicio.transferir(A,B,M).getEstado()).isEqualTo(RECHAZADA);
        verify(cuentas,never()).cargar(any(),any());
        verifyNoInteractions(movimientos,avisos);
    }
    @Test void saldoInsuficienteNoGeneraDevolucion() {
        when(cuentas.cargar(A,M)).thenThrow(new ErrorRemotoException("cuenta",422,"saldo insuficiente"));
        assertThat(servicio.transferir(A,B,M).getEstado()).isEqualTo(RECHAZADA);
        verify(cuentas,never()).abonar(any(),any());
    }
    @Test void falloDeLedgerConAbonoConfirmadoNoDuplicaDinero() {
        exito();
        when(movimientos.registrar(any(),any(),any(),any(),any())).thenThrow(new RuntimeException("ledger apagado"));
        assertThat(servicio.transferir(A,B,M).getEstado()).isEqualTo(REGISTRO_PENDIENTE);
        verify(cuentas,never()).abonar(eq(A),any());
        verifyNoInteractions(avisos);
    }
    @Test void falloDeLedgerTrasDevolucionNoVuelveADevolver() {
        rechazarAbono();
        when(cuentas.abonar(A,M)).thenReturn(new CuentaRemota(A,"Origen",new BigDecimal("1000")));
        when(movimientos.registrar(any(),any(),any(),any(),any())).thenThrow(new RuntimeException("ledger apagado"));
        Transferencia t = servicio.transferir(A,B,M);
        assertThat(t.getEstado()).isEqualTo(REGISTRO_PENDIENTE);
        assertThat(t.getDetalle()).contains("ya devuelto");
        verify(cuentas,times(1)).abonar(A,M);
    }
    @Test void falloDeAvisoNoRevierteTransferenciaCompletada() {
        exito();
        doThrow(new RuntimeException("avisos apagados")).when(avisos).notificar(any());
        Transferencia t = servicio.transferir(A,B,M);
        assertThat(t.getEstado()).isEqualTo(COMPLETADA);
        assertThat(t.getDetalle()).contains("aviso pendiente");
        verify(cuentas,never()).abonar(eq(A),any());
    }
    @Test void mismaCuentaYMontoInvalidoNoCreanEfectos() {
        assertThatThrownBy(() -> servicio.transferir(A,A,M)).isInstanceOf(ReglaDeNegocioException.class);
        for (String monto : List.of("0","-1","0.001"))
            assertThatThrownBy(() -> servicio.transferir(A,B,new BigDecimal(monto))).isInstanceOf(ReglaDeNegocioException.class);
        verifyNoInteractions(cuentas,repo,movimientos,avisos);
    }
}
