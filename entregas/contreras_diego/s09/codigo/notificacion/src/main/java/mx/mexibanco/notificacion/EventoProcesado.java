package mx.mexibanco.notificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eventos_procesados")
public class EventoProcesado {

    @Id
    @Column(name = "evento_id")
    private UUID eventoId;

    @Column(nullable = false)
    private String tipo;

    @Column(name = "transferencia_id")
    private Long transferenciaId;

    @Column(nullable = false)
    private Instant procesado = Instant.now();

    public EventoProcesado() {}

    public EventoProcesado(UUID eventoId, String tipo, Long transferenciaId) {
        this.eventoId = eventoId;
        this.tipo = tipo;
        this.transferenciaId = transferenciaId;
        this.procesado = Instant.now();
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

    public Instant getProcesado() {
        return procesado;
    }
}
