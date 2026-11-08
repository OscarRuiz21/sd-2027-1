package mx.mexibanco.notificacion;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

/** Un evento ya procesado. La llave primaria es el eventoId: el mismo evento no entra dos veces. */
@Entity
public class EventoProcesado {

    @Id
    private UUID eventoId;
    private String tipo;
    private Long transferenciaId;
    private Instant fecha;

    protected EventoProcesado() {
    }
}
