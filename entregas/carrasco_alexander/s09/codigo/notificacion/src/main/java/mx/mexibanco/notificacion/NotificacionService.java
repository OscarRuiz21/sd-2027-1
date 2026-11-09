package mx.mexibanco.notificacion;

import mx.mexibanco.notificacion.mensajeria.TransferenciaEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cuarto concepto de Mexi Banco. Guarda el aviso en su base y lo escribe en el log. En el monolito
 * esto ocurria dentro de la misma transaccion que el movimiento; en v06a es una llamada HTTP aparte
 * y ya no hay transaccion que las una.
 *
 * LAB S09: este servicio tambien guarda los avisos que llegan por RabbitMQ (ver
 * mensajeria.ConsumidorDeTransferencias).
 */
@Service
public class NotificacionService {

        private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

        private final NotificacionRepository notificaciones;
        private final EventoProcesadoRepository eventos;

        public NotificacionService(NotificacionRepository notificaciones, EventoProcesadoRepository eventos) {
                this.notificaciones = notificaciones;
                this.eventos = eventos;
        }

        @Transactional
        public Notificacion notificar(AvisoDeMovimiento movimiento) {
                String mensaje = movimiento.tipo() + " por " + movimiento.monto().abs() + ", saldo resultante " + movimiento.saldoResultante();
                log.info("[notificacion] {} · {}", movimiento.clabeCuenta(), mensaje);
                return notificaciones.save(new Notificacion(movimiento.clabeCuenta(), mensaje));
        }

        /**
         * Guarda los avisos de un evento de transferencia. Es idempotente: si el eventoId ya estaba
         * registrado, no hace nada y regresa false. Si es nuevo, guarda el eventoId (restriccion UNIQUE)
         * y los avisos en la misma transaccion y regresa true.
         */
        @Transactional
        public boolean avisarTransferencia(TransferenciaEvento evento) {
                if (eventos.existsByEventoId(evento.eventoId())) {
                        log.info("[notificacion] evento {} ya procesado; se ignora", evento.eventoId());
                        return false;
                }
                // Mismo commit que los avisos: o se guardan todos, o ninguno.
                eventos.saveAndFlush(new EventoProcesado(evento.eventoId()));
                switch (evento.tipo()) {
                        case "TransferenciaRealizada" -> {
                                guardarAviso(evento.clabeOrigen(), "Enviaste " + evento.monto() + " a la cuenta "
                                        + evento.clabeDestino() + ", saldo resultante " + evento.saldoOrigen());
                                guardarAviso(evento.clabeDestino(), "Recibiste " + evento.monto() + " de la cuenta "
                                        + evento.clabeOrigen() + ", saldo resultante " + evento.saldoDestino());
                        }
                        case "TransferenciaRevertida" -> guardarAviso(evento.clabeOrigen(),
                                "Tu transferencia no se realizó y te devolvimos el dinero");
                        default -> log.warn("[notificacion] evento desconocido: {}", evento.tipo());
                }
                return true;
        }

        private void guardarAviso(String clabe, String mensaje) {
                log.info("[notificacion] {} - {}", clabe, mensaje);
                notificaciones.save(new Notificacion(clabe, mensaje));
        }

        @Transactional(readOnly = true)
        public List<Notificacion> bandeja(String clabeCuenta) {
                return notificaciones.findByClabeCuentaOrderByFechaDesc(clabeCuenta);
        }
}