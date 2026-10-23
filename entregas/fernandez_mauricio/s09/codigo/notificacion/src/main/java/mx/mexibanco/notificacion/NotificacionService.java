package mx.mexibanco.notificacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Guarda los avisos en la base de datos y registra su contenido en el log.
 * Los avisos pueden originarse por HTTP o por eventos recibidos desde RabbitMQ.
 */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final NotificacionRepository notificaciones;
    private final EventoProcesadoRepository procesados;

    public NotificacionService(
            NotificacionRepository notificaciones,
            EventoProcesadoRepository procesados) {
        this.notificaciones = notificaciones;
        this.procesados = procesados;
    }

    @Transactional
    public Notificacion notificar(AvisoDeMovimiento movimiento) {
        String mensaje = movimiento.tipo() + " por " + movimiento.monto().abs()
                + ", saldo resultante " + movimiento.saldoResultante();
        log.info("[notificacion] {} · {}", movimiento.clabeCuenta(), mensaje);
        return notificaciones.save(new Notificacion(movimiento.clabeCuenta(), mensaje));
    }

    @Transactional
    public List<Notificacion> avisarDeTransferencia(EventoTransferencia evento) {
        if (procesados.marcarSiEsNuevo(
                evento.eventoId(),
                evento.tipo(),
                evento.transferenciaId()) == 0) {
            return List.of();
        }

        List<Notificacion> avisos = switch (evento.tipo()) {
            case EventoTransferencia.REALIZADA -> List.of(
                    aviso(evento.clabeOrigen(), "TRANSFERENCIA_ENVIADA",
                            evento.monto(), evento.saldoOrigen()),
                    aviso(evento.clabeDestino(), "TRANSFERENCIA_RECIBIDA",
                            evento.monto(), evento.saldoDestino()));
            case EventoTransferencia.REVERTIDA -> List.of(
                    aviso(evento.clabeOrigen(), "DEVOLUCION_TRANSFERENCIA",
                            evento.monto(), evento.saldoOrigen()));
            default -> throw new IllegalArgumentException(
                    "Tipo de evento desconocido: " + evento.tipo());
        };
        return notificaciones.saveAll(avisos);
    }

    /** El mismo texto de siempre: tipo, monto sin signo y saldo resultante. */
    private static Notificacion aviso(String clabe, String tipo, BigDecimal monto, BigDecimal saldo) {
        String mensaje = tipo + " por " + monto.abs() + ", saldo resultante " + saldo;
        log.info("[notificacion] {} · {}", clabe, mensaje);
        return new Notificacion(clabe, mensaje);
    }

    @Transactional(readOnly = true)
    public List<Notificacion> bandeja(String clabeCuenta) {
        return notificaciones.findByClabeCuentaOrderByFechaDesc(clabeCuenta);
    }
}