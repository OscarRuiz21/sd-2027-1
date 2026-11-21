package mx.mexibanco.transferencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import mx.mexibanco.mensajeria.EventoTransferencia;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evento_outbox")
public class EventoOutbox {

    @Id
    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(name = "transferencia_id", nullable = false)
    private Long transferenciaId;

    @Column(name = "clabe_origen", nullable = false, length = 18)
    private String clabeOrigen;

    @Column(name = "clabe_destino", nullable = false, length = 18)
    private String clabeDestino;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monto;

    @Column(name = "saldo_origen", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoOrigen;

    @Column(name = "saldo_destino", precision = 19, scale = 2)
    private BigDecimal saldoDestino;

    @Column(nullable = false)
    private Instant fecha;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "publicado_en")
    private Instant publicadoEn;

    protected EventoOutbox() {
    }

    public EventoOutbox(EventoTransferencia evento) {
        this.eventoId = evento.eventoId();
        this.tipo = evento.tipo();
        this.transferenciaId = evento.transferenciaId();
        this.clabeOrigen = evento.clabeOrigen();
        this.clabeDestino = evento.clabeDestino();
        this.monto = evento.monto();
        this.saldoOrigen = evento.saldoOrigen();
        this.saldoDestino = evento.saldoDestino();
        this.fecha = evento.fecha();
        this.creadoEn = Instant.now();
    }

    public EventoTransferencia evento() {
        return new EventoTransferencia(eventoId, tipo, transferenciaId, clabeOrigen, clabeDestino,
            monto, saldoOrigen, saldoDestino, fecha);
    }

    public void marcarPublicado() {
        publicadoEn = Instant.now();
    }

    public UUID getEventoId() {
        return eventoId;
    }
}
