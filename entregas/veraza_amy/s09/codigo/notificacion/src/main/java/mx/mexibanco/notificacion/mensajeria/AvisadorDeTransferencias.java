package mx.mexibanco.notificacion.mensajeria;
import mx.mexibanco.notificacion.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service
public class AvisadorDeTransferencias {
    private final EventosProcesados procesados;
    private final NotificacionRepository avisos;
    public AvisadorDeTransferencias(EventosProcesados procesados, NotificacionRepository avisos) {
        this.procesados = procesados; this.avisos = avisos;
    }
    @Transactional
    public int avisar(EventoTransferencia e) {
        e.validar();
        if (procesados.marcarSiEsNuevo(e.eventoId(), e.tipo(), e.transferenciaId()) == 0) return 0;
        if ("TransferenciaRealizada".equals(e.tipo())) {
            avisos.saveAll(List.of(
                new Notificacion(e.clabeOrigen(), "TRANSFERENCIA_ENVIADA por " + e.monto() + ", saldo resultante " + e.saldoOrigen()),
                new Notificacion(e.clabeDestino(), "TRANSFERENCIA_RECIBIDA por " + e.monto() + ", saldo resultante " + e.saldoDestino())));
            return 2;
        }
        avisos.save(new Notificacion(e.clabeOrigen(), "DEVOLUCION_TRANSFERENCIA por " + e.monto()
            + ": tu transferencia no se realizo y te devolvimos el dinero, saldo resultante " + e.saldoOrigen()));
        return 1;
    }
}
