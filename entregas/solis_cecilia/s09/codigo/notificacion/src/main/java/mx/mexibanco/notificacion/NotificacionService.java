package mx.mexibanco.notificacion;

import mx.mexibanco.notificacion.mensajeria.EventoTransferencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

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

    // Se conserva para los clientes HTTP, como SPEI.
    @Transactional
    public Notificacion notificar(AvisoDeMovimiento movimiento) {
        String mensaje = movimiento.tipo()
                + " por " + movimiento.monto().abs()
                + ", saldo resultante " + movimiento.saldoResultante();

        log.info("[notificacion] {} · {}", movimiento.clabeCuenta(), mensaje);

        return notificaciones.save(
                new Notificacion(movimiento.clabeCuenta(), mensaje));
    }

    /**
     * Registra el evento y sus avisos dentro de la misma transacción.
     */
    @Transactional
    public void recibirEvento(EventoTransferencia evento) {
        validarEvento(evento);

        int insertados =
                eventosProcesados.registrarSiNuevo(evento.eventoId());

        if (insertados == 0) {
            log.info(
                    "Evento duplicado ignorado: eventoId={}",
                    evento.eventoId());
            return;
        }

        if ("TransferenciaRealizada".equals(evento.tipo())) {
            notificaciones.save(new Notificacion(
                    evento.clabeOrigen(),
                    "Transferencia " + evento.transferenciaId()
                            + " enviada por " + evento.monto()
                            + ", saldo resultante " + evento.saldoOrigen()));

            notificaciones.save(new Notificacion(
                    evento.clabeDestino(),
                    "Transferencia " + evento.transferenciaId()
                            + " recibida por " + evento.monto()
                            + ", saldo resultante " + evento.saldoDestino()));

        } else {
            notificaciones.save(new Notificacion(
                    evento.clabeOrigen(),
                    "Tu transferencia " + evento.transferenciaId()
                            + " no se realizó y te devolvimos "
                            + evento.monto()
                            + ", saldo resultante " + evento.saldoOrigen()));
        }

        log.info(
                "Avisos preparados: eventoId={}, tipo={}, transferenciaId={}",
                evento.eventoId(),
                evento.tipo(),
                evento.transferenciaId());
    }

    private void validarEvento(EventoTransferencia evento) {
        if (evento == null
                || evento.eventoId() == null
                || evento.eventoId().isBlank()
                || evento.eventoId().length() > 100
                || evento.tipo() == null
                || !Set.of("TransferenciaRealizada", "TransferenciaRevertida")
                        .contains(evento.tipo())
                || evento.transferenciaId() == null
                || evento.clabeOrigen() == null
                || evento.clabeOrigen().isBlank()
                || evento.clabeOrigen().length() > 18
                || evento.clabeDestino() == null
                || evento.clabeDestino().isBlank()
                || evento.clabeDestino().length() > 18
                || evento.monto() == null
                || evento.monto().signum() <= 0
                || evento.saldoOrigen() == null
                || evento.fecha() == null) {

            throw new IllegalArgumentException(
                    "Evento de transferencia inválido");
        }

        if ("TransferenciaRealizada".equals(evento.tipo())
                && evento.saldoDestino() == null) {

            throw new IllegalArgumentException(
                    "Una transferencia realizada necesita saldoDestino");
        }
    }

    @Transactional(readOnly = true)
    public List<Notificacion> bandeja(String clabeCuenta) {
        return notificaciones.findByClabeCuentaOrderByFechaDesc(clabeCuenta);
    }
}