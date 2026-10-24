package mx.mexibanco.notificacion.mensajeria;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contrato compartido por JSON, sin depender de clases Java del productor. */
public record EventoTransferencia(String eventoId, String tipo, Long transferenciaId,
        String clabeOrigen, String clabeDestino, BigDecimal monto,
        BigDecimal saldoOrigen, BigDecimal saldoDestino, Instant fecha) {

    public void validar() {
        if (eventoId == null || tipo == null || transferenciaId == null || transferenciaId <= 0
                || clabeOrigen == null || !clabeOrigen.matches("[0-9]{18}")
                || clabeDestino == null || !clabeDestino.matches("[0-9]{18}")
                || clabeOrigen.equals(clabeDestino)
                || monto == null || monto.signum() <= 0 || saldoOrigen == null || fecha == null) {
            throw new IllegalArgumentException("Evento incompleto o con campos invalidos");
        }
        UUID.fromString(eventoId);
        if (!tipo.equals("TransferenciaRealizada") && !tipo.equals("TransferenciaRevertida")) {
            throw new IllegalArgumentException("Tipo de evento desconocido: " + tipo);
        }
        if (tipo.equals("TransferenciaRealizada") && saldoDestino == null) {
            throw new IllegalArgumentException("Falta saldoDestino en TransferenciaRealizada");
        }
    }
}
