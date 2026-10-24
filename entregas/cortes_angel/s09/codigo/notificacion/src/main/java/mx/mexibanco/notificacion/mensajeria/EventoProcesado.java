package mx.mexibanco.notificacion.mensajeria;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

@Entity
public class EventoProcesado {
    @Id
    private UUID eventoId;
    private String tipo;
    private Long transferenciaId;
    
    protected EventoProcesado() {}
}