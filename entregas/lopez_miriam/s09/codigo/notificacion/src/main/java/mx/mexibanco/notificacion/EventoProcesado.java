package mx.mexibanco.notificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * LAB S09: registra los eventos de RabbitMQ que ya fueron procesados.
 *
 * El eventoId es la clave primaria para impedir que un mismo
 * evento se registre dos veces.
 */
@Entity
@Table(name = "eventos_procesados")
public class EventoProcesado {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID eventoId;

    @Column(nullable = false)
    private Instant fechaProcesamiento;

    protected EventoProcesado() {
        // Constructor requerido por JPA.
    }

    public EventoProcesado(UUID eventoId) {
        this.eventoId = eventoId;
        this.fechaProcesamiento = Instant.now();
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public Instant getFechaProcesamiento() {
        return fechaProcesamiento;
    }
}
