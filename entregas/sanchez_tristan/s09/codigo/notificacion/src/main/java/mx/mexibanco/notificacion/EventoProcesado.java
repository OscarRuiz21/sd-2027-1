package mx.mexibanco.notificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de eventos ya procesados para garantizar idempotencia en el consumidor.
 * El eventoId es la llave primaria (o restricción UNIQUE), por lo que un evento
 * repetido no se inserta gracias a ON CONFLICT en la sentencia de inserción.
 */
@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {

    @Id
    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(name = "tipo", nullable = false, length = 50)
    private String tipo;

    @Column(name = "transferencia_id", nullable = false)
    private Long transferenciaId;

    @Column(name = "fecha", nullable = false)
    private Instant fecha;

    protected EventoProcesado() {
    }

    public EventoProcesado(UUID eventoId, String tipo, Long transferenciaId) {
        this.eventoId = eventoId;
        this.tipo = tipo;
        this.transferenciaId = transferenciaId;
        this.fecha = Instant.now();
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public String getTipo() {
        return tipo;
    }

    public Long getTransferenciaId() {
        return transferenciaId;
    }

    public Instant getFecha() {
        return fecha;
    }
}
