package mx.mexibanco.transferencia;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class Transferencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 18)
    private String claveOrigen;
    @Column(nullable = false, length = 18)
    private String claveDestino;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monto;
    @Column(nullable = false)
    private Instant fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EstadoTransferencia estado;
    @Column(nullable = false)
    private Instant actualizada;
    @Column(length = 2000)
    private String detalle;

    protected Transferencia() { }

    public Transferencia(String origen, String destino, BigDecimal monto) {
        this.claveOrigen = origen;
        this.claveDestino = destino;
        this.monto = monto;
        this.fecha = Instant.now();
        cambiarEstado(EstadoTransferencia.INICIADA, "Transferencia iniciada");
    }

    public void cambiarEstado(EstadoTransferencia estado, String detalle) {
        this.estado = estado;
        this.detalle = detalle == null ? null : detalle.substring(0, Math.min(2000, detalle.length()));
        this.actualizada = Instant.now();
    }

    public Long getId() { return id; }
    public String getClaveOrigen() { return claveOrigen; }
    public String getClaveDestino() { return claveDestino; }
    public BigDecimal getMonto() { return monto; }
    public Instant getFecha() { return fecha; }
    public EstadoTransferencia getEstado() { return estado; }
    public Instant getActualizada() { return actualizada; }
    public String getDetalle() { return detalle; }
}