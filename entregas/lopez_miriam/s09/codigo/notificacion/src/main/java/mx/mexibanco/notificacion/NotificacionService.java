package mx.mexibanco.notificacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class NotificacionService {

    private static final Logger log =
            LoggerFactory.getLogger(NotificacionService.class);

    private final NotificacionRepository notificaciones;
    private final EventoProcesadoRepository eventosProcesados;

    public NotificacionService(
            NotificacionRepository notificaciones,
            EventoProcesadoRepository eventosProcesados) {

        this.notificaciones = notificaciones;
        this.eventosProcesados = eventosProcesados;
    }

    @Transactional
    public Notificacion notificar(AvisoDeMovimiento movimiento) {

        String mensaje = movimiento.tipo()
                + " por " + movimiento.monto().abs()
                + ", saldo resultante "
                + movimiento.saldoResultante();

        log.info("[notificacion] {} · {}",
                movimiento.clabeCuenta(), mensaje);

        return notificaciones.save(
                new Notificacion(movimiento.clabeCuenta(), mensaje));
    }

    /**
     * LAB S09: procesamiento idempotente.
     *
     * El evento se registra una sola vez en PostgreSQL.
     * Si ya existe, no se generan nuevos avisos.
     *
     * El registro del evento y los avisos forman una sola transaccion.
     */
    @Transactional
    public List<Notificacion> avisarDeTransferencia(
            EventoTransferencia evento) {

        if (evento.eventoId() == null) {
            throw new IllegalArgumentException(
                    "El evento no contiene eventoId");
        }

        if (!EventoTransferencia.REALIZADA.equals(evento.tipo())
                && !EventoTransferencia.REVERTIDA.equals(evento.tipo())) {

            throw new IllegalArgumentException(
                    "Tipo de evento desconocido: " + evento.tipo());
        }

        int registrados =
                eventosProcesados.registrarSiEsNuevo(evento.eventoId());

        if (registrados == 0) {
            log.info(
                    "[idempotencia] Evento {} duplicado; no se crean avisos",
                    evento.eventoId()
            );
            return List.of();
        }

        List<Notificacion> avisos = switch (evento.tipo()) {

            case EventoTransferencia.REALIZADA -> List.of(
                aviso(
                    evento.clabeOrigen(),
                    "TRANSFERENCIA_ENVIADA",
                    evento.monto(),
                    evento.saldoOrigen()
                ),
                aviso(
                    evento.clabeDestino(),
                    "TRANSFERENCIA_RECIBIDA",
                    evento.monto(),
                    evento.saldoDestino()
                )
            );

            case EventoTransferencia.REVERTIDA -> List.of(
                aviso(
                    evento.clabeOrigen(),
                    "DEVOLUCION_TRANSFERENCIA",
                    evento.monto(),
                    evento.saldoOrigen()
                )
            );

            default -> throw new IllegalArgumentException(
                    "Tipo de evento desconocido: " + evento.tipo());
        };

        List<Notificacion> guardados =
                notificaciones.saveAll(avisos);

        log.info(
                "[idempotencia] Evento {} registrado; {} aviso(s) guardado(s)",
                evento.eventoId(),
                guardados.size()
        );

        return guardados;
    }

    private static Notificacion aviso(
            String clabe,
            String tipo,
            BigDecimal monto,
            BigDecimal saldo) {

        String mensaje = tipo
                + " por " + monto.abs()
                + ", saldo resultante " + saldo;

        log.info("[notificacion] {} · {}", clabe, mensaje);

        return new Notificacion(clabe, mensaje);
    }

    @Transactional(readOnly = true)
    public List<Notificacion> bandeja(String clabeCuenta) {
        return notificaciones.findByClabeCuentaOrderByFechaDesc(
                clabeCuenta);
    }
}
