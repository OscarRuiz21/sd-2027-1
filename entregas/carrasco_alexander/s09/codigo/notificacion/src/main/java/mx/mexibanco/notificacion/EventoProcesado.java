package mx.mexibanco.notificacion;

import jakarta.persistence.*;

import java.time.Instant;

/** Registro de los eventos ya procesados. La restriccion UNIQUE sobre evento_id evita procesar dos veces el mismo. */
@Entity
@Table(name = "evento_procesado",
        uniqueConstraints = @UniqueConstraint(name = "uk_evento_procesado_evento_id", columnNames = "evento_id"))
public class EventoProcesado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false, length = 120)
    private String eventoId;

    @Column(nullable = false)
    private Instant fecha;

    protected EventoProcesado() {
    }

    public EventoProcesado(String eventoId) {
        this.eventoId = eventoId;
        this.fecha = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEventoId() {
        return eventoId;
    }

    public Instant getFecha() {
        return fecha;
    }
}