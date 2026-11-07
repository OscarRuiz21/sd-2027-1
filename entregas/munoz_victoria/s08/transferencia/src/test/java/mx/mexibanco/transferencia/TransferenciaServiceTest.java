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

import static org.assertj.core.api.Assertions.*;
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

    @BeforeEach
        void armar() {
          servicio = new TransferenciaService(
            transferencias,
            cuentas,
            movimientos,
            notificaciones,
            0
    );

    lenient().when(transferencias.save(any(Transferencia.class)))
            .thenAnswer(i -> i.getArgument(0));
}

    @Test
    void transferenciaExitosaQuedaCompletada() {
        when(cuentas.cargar(ORIGEN, MONTO))
                .thenReturn(new CuentaRemota(
                        ORIGEN, "Ana", new BigDecimal("800")
                ));

        when(cuentas.abonar(DESTINO, MONTO))
                .thenReturn(new CuentaRemota(
                        DESTINO, "Beto", new BigDecimal("700")
                ));

        when(movimientos.registrar(any(), any(), any(), any(), any()))
                .thenReturn(mock(MovimientoRemoto.class));

        Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(t.getEstado())
                .isEqualTo(EstadoTransferencia.COMPLETADA);

        verify(notificaciones, times(2)).notificar(any());
    }

    @Test
    void cuentaDestinoInexistenteNoRealizaCargo() {
        when(cuentas.consultar(ORIGEN))
                .thenReturn(new CuentaRemota(
                        ORIGEN, "Ana", new BigDecimal("1000")
                ));

        doThrow(new ErrorRemotoException(
                "cuenta", 404, "CLABE inexistente"
        )).when(cuentas).consultar(DESTINO);

        assertThatThrownBy(() ->
                servicio.transferir(ORIGEN, DESTINO, MONTO)
        ).isInstanceOf(ErrorRemotoException.class);

        verify(cuentas, never()).cargar(any(), any());
    }

    @Test
    void rechazaTransferenciaALaMismaCuenta() {
        assertThatThrownBy(() ->
                servicio.transferir(ORIGEN, ORIGEN, MONTO)
        ).isInstanceOf(ReglaDeNegocioException.class);

        verifyNoInteractions(
                cuentas,
                movimientos,
                transferencias,
                notificaciones
        );
    }

    @Test
    void siFallaAbonoDevuelveDineroYRegistraReembolso() {
        when(cuentas.cargar(ORIGEN, MONTO))
                .thenReturn(new CuentaRemota(
                        ORIGEN, "Ana", new BigDecimal("800")
                ));

        when(cuentas.abonar(DESTINO, MONTO))
                .thenThrow(new ErrorRemotoException(
                        "cuenta", 503, "Servicio no disponible"
                ));

        when(cuentas.abonar(ORIGEN, MONTO))
                .thenReturn(new CuentaRemota(
                        ORIGEN, "Ana", new BigDecimal("1000")
                ));

        when(movimientos.registrar(any(), any(), any(), any(), any()))
                .thenReturn(mock(MovimientoRemoto.class));

        Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(t.getEstado())
                .isEqualTo(EstadoTransferencia.COMPENSADA);

        verify(movimientos).registrar(
                eq(ORIGEN),
                eq(TipoMovimiento.TRANSFERENCIA_REEMBOLSADA),
                eq(MONTO),
                eq(new BigDecimal("1000")),
                any()
        );

        verifyNoInteractions(notificaciones);
    }

    @Test
    void siFallaElReembolsoQuedaPendiente() {
        when(cuentas.cargar(ORIGEN, MONTO))
                .thenReturn(new CuentaRemota(
                        ORIGEN, "Ana", new BigDecimal("800")
                ));

        when(cuentas.abonar(DESTINO, MONTO))
                .thenThrow(new ErrorRemotoException(
                        "cuenta", 503, "Servicio no disponible"
                ));

        when(cuentas.abonar(ORIGEN, MONTO))
                .thenThrow(new ErrorRemotoException(
                        "cuenta", 503, "Sigue sin servicio"
                ));

        Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

        assertThat(t.getEstado())
                .isEqualTo(EstadoTransferencia.COMPENSACION_PENDIENTE);
    }
}