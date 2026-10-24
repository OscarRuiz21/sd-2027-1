package mx.mexibanco.notificacion;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class EventoProcesado {
    
    @Id
    private String eventoId;

    protected EventoProcesado() {}

    public EventoProcesado(String eventoId) {
        this.eventoId = eventoId;
    }

    public String getEventoId() {
        return eventoId;
    }
}