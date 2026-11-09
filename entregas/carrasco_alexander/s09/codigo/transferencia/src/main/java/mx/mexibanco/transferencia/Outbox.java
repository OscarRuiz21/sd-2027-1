package mx.mexibanco.transferencia;

import jakarta.persistence.*;

import java.time.Instant;

/** Evento pendiente de publicar. Se escribe en la misma transaccion que el cambio de estado de la transferencia. */
@Entity
@Table(name = "outbox")
public class Outbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String eventoId;

    @Column(nullable = false, length = 80)
    private String routingKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private Instant creada;

    /** null = pendiente de publicar. */
    private Instant publicada;

    protected Outbox() {
    }

    public Outbox(String eventoId, String routingKey, String payload) {
        this.eventoId = eventoId;
        this.routingKey = routingKey;
        this.payload = payload;
        this.creada = Instant.now();
    }

    public Long getId() { return id; }
    public String getEventoId() { return eventoId; }
    public String getRoutingKey() { return routingKey; }
    public String getPayload() { return payload; }
    public Instant getCreada() { return creada; }
    public Instant getPublicada() { return publicada; }

    public void marcarPublicada() {
        this.publicada = Instant.now();
    }
}