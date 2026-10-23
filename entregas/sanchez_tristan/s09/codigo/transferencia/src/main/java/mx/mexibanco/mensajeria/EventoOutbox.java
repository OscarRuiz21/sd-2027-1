package mx.mexibanco.mensajeria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Tabla outbox en la base de transferencia.
 * Se guarda en la MISMA transacción local que marca la transferencia como COMPLETADA o COMPENSADA.
 * El relay programado se encarga de publicar las filas pendientes al broker.
 */
@Entity
@Table(name = "outbox")
public class EventoOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false, unique = true)
    private UUID eventoId;

    @Column(name = "tipo", nullable = false, length = 50)
    private String tipo;

    @Column(name = "transferencia_id", nullable = false)
    private Long transferenciaId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "publicado", nullable = false)
    private boolean publicado;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "publicado_en")
    private Instant publicadoEn;

    protected EventoOutbox() {
    }

    public EventoOutbox(UUID eventoId, String tipo, Long transferenciaId, String payload) {
        this.eventoId = eventoId;
        this.tipo = tipo;
        this.transferenciaId = transferenciaId;
        this.payload = payload;
        this.publicado = false;
        this.creadoEn = Instant.now();
    }

    public void marcarPublicado() {
        this.publicado = true;
        this.publicadoEn = Instant.now();
    }

    public Long getId() {
        return id;
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

    public String getPayload() {
        return payload;
    }

    public boolean isPublicado() {
        return publicado;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getPublicadoEn() {
        return publicadoEn;
    }
}
