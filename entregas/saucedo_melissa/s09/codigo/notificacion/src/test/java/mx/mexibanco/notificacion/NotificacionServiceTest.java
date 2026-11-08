package mx.mexibanco.notificacion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Nueva en v06a: el mensaje del aviso se arma igual que en el monolito, ahora desde un DTO. */
@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

        private static final UUID EVENTO = UUID.fromString("765a4aa3-e1d4-44ad-bda8-459910e29239");

        @Mock
        NotificacionRepository notificaciones;

        @Mock
        EventoProcesadoRepository procesados;

        @InjectMocks
        NotificacionService servicio;

        @Test
        void elAvisoDiceTipoMontoSinSignoYSaldo() {
                when(notificaciones.save(any(Notificacion.class))).thenAnswer(i -> i.getArgument(0));

                Notificacion aviso = servicio.notificar(new AvisoDeMovimiento("002180000000000001",
                        "TRANSFERENCIA_ENVIADA", new BigDecimal("-200"), new BigDecimal("800")));

                assertThat(aviso.getClabeCuenta()).isEqualTo("002180000000000001");
                assertThat(aviso.getMensaje()).isEqualTo("TRANSFERENCIA_ENVIADA por 200, saldo resultante 800");
        }

        /** Un evento nuevo guarda los dos avisos: uno para quien envio y otro para quien recibio. */
        @Test
        void unEventoNuevoGuardaLosDosAvisos() {
                when(procesados.marcarSiEsNuevo(EVENTO, EventoTransferencia.REALIZADA, 15L)).thenReturn(1);
                when(notificaciones.saveAll(any())).thenAnswer(i -> i.getArgument(0));

                List<Notificacion> avisos = servicio.avisarDeTransferencia(realizada());

                assertThat(avisos).hasSize(2);
                assertThat(avisos.get(0).getMensaje()).isEqualTo("TRANSFERENCIA_ENVIADA por 20, saldo resultante 744.00");
                assertThat(avisos.get(1).getMensaje()).isEqualTo("TRANSFERENCIA_RECIBIDA por 20, saldo resultante 756.00");
        }

        /** El mismo evento entregado otra vez no guarda nada: la idempotencia por eventoId. */
        @Test
        void unEventoRepetidoNoGuardaNada() {
                when(procesados.marcarSiEsNuevo(EVENTO, EventoTransferencia.REALIZADA, 15L)).thenReturn(0);

                List<Notificacion> avisos = servicio.avisarDeTransferencia(realizada());

                assertThat(avisos).isEmpty();
                verify(notificaciones, never()).saveAll(any());
        }

        private static EventoTransferencia realizada() {
                return new EventoTransferencia(EVENTO, EventoTransferencia.REALIZADA, 15L, "002180000000000001",
                        "002180000000000002", new BigDecimal("20"), new BigDecimal("744.00"), new BigDecimal("756.00"),
                        Instant.parse("2026-11-07T02:22:17Z"));
        }
}
