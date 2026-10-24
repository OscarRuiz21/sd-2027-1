package mx.mexibanco.notificacion.mensajeria;

import mx.mexibanco.notificacion.Notificacion;
import mx.mexibanco.notificacion.NotificacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcesadorDeTransferencias {
    private final NotificacionRepository notificaciones;
    private final EventoProcesadoRepository procesados;

    public ProcesadorDeTransferencias(NotificacionRepository notificaciones,
                                     EventoProcesadoRepository procesados) {
        this.notificaciones = notificaciones;
        this.procesados = procesados;
    }

    /** El proxy de Spring confirma esta transaccion ANTES de volver al listener. */
    @Transactional
    public boolean procesar(EventoTransferencia e) {
        e.validar();
        if (procesados.registrarSiNuevo(e.eventoId()) == 0) {
            return false;
        }
        if (e.tipo().equals("TransferenciaRealizada")) {
            notificaciones.save(new Notificacion(e.clabeOrigen(),
                "Transferencia #" + e.transferenciaId() + " enviada por " + e.monto().toPlainString()
                    + " a " + e.clabeDestino() + ". Saldo: " + e.saldoOrigen().toPlainString()));
            notificaciones.save(new Notificacion(e.clabeDestino(),
                "Transferencia #" + e.transferenciaId() + " recibida por " + e.monto().toPlainString()
                    + " de " + e.clabeOrigen() + ". Saldo: " + e.saldoDestino().toPlainString()));
        } else {
            notificaciones.save(new Notificacion(e.clabeOrigen(),
                "Tu transferencia no se realizó y te devolvimos el dinero. Transferencia #"
                    + e.transferenciaId() + "; monto: " + e.monto().toPlainString()
                    + "; saldo: " + e.saldoOrigen().toPlainString()));
        }
        // El marcador y todos los avisos se guardan o revierten juntos.
        // Si falla un save o el commit, el listener no confirma y se puede reintentar.
        return true;
    }
}
