package mx.mexibanco.notificacion;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class EventoProcesado {
    @Id
    private String eventoId;
    
    public EventoProcesado() {}
    
    public EventoProcesado(String eventoId) {
        this.eventoId = eventoId;
    }
    
    public String getEventoId() { return eventoId; }
    public void setEventoId(String eventoId) { this.eventoId = eventoId; }
}