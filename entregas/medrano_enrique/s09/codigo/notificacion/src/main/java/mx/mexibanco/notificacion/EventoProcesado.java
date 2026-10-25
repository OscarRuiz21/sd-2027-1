package mx.mexibanco.notificacion;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class EventoProcesado {
    @Id
    private UUID eventoId; // Esta será la llave primaria (única)
    private String tipo;
    private Long transferenciaId;

    protected EventoProcesado() {}

    public EventoProcesado(UUID eventoId, String tipo, Long transferenciaId) {
        this.eventoId = eventoId;
        this.tipo = tipo;
        this.transferenciaId = transferenciaId;
    }
}