package mx.mexibanco.transferencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class EventoOutbox {
    @Id
    private String id;
    private String routingKey;
    @Column(length = 2000)
    private String payload;
    private boolean procesado;

    protected EventoOutbox() {}

    public EventoOutbox(String routingKey, String payload) {
        this.id = UUID.randomUUID().toString();
        this.routingKey = routingKey;
        this.payload = payload;
        this.procesado = false;
    }

    public String getId() { return id; }
    public String getRoutingKey() { return routingKey; }
    public String getPayload() { return payload; }
    public void setProcesado(boolean procesado) { this.procesado = procesado; }
}