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
 * Una fila de la tabla outbox: un evento que la saga ya decidio publicar pero que todavia puede no
 * haber salido hacia RabbitMQ. Se escribe en la MISMA transaccion que el estado final de la
 * transferencia; el relay la publica despues y le pone publicadoEn.
 */
@Entity
@Table(name = "outbox")
public class EventoSaliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Es el eventoId de la transferencia: no cambia aunque el evento se publique varias veces. */
    @Column(name = "evento_id", nullable = false, unique = true)
    private UUID eventoId;

    @Column(name = "routing_key", nullable = false)
    private String routingKey;

    /** El evento ya serializado a JSON. */
    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    /** null = pendiente de publicar. */
    @Column(name = "publicado_en")
    private Instant publicadoEn;

    protected EventoSaliente() {
    }

    public EventoSaliente(UUID eventoId, String routingKey, String payload) {
        this.eventoId = eventoId;
        this.routingKey = routingKey;
        this.payload = payload;
        this.creadoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getPublicadoEn() {
        return publicadoEn;
    }

    public void marcarPublicado() {
        this.publicadoEn = Instant.now();
    }
}