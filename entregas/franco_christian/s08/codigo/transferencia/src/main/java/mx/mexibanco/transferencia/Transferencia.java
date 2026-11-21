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
    @Column(nullable = false, length = 32)
    private EstadoTransferencia estado = EstadoTransferencia.INICIADA;

    @Column(length = 500)
    private String motivoFallo;

    @Column(nullable = false)
    private Instant actualizadaEn = Instant.now();

    protected Transferencia() {
    }

    public Transferencia(String claveOrigen, String claveDestino, BigDecimal monto) {
        this.claveOrigen = claveOrigen;
        this.claveDestino = claveDestino;
        this.monto = monto;
        this.fecha = Instant.now();
        this.actualizadaEn = this.fecha;
        this.estado = EstadoTransferencia.INICIADA;
    }

    public Long getId() { return id; }
    public String getClaveOrigen() { return claveOrigen; }
    public String getClaveDestino() { return claveDestino; }
    public BigDecimal getMonto() { return monto; }
    public Instant getFecha() { return fecha; }
    public EstadoTransferencia getEstado() { return estado; }
    public String getMotivoFallo() { return motivoFallo; }
    public Instant getActualizadaEn() { return actualizadaEn; }

    public void setEstado(EstadoTransferencia estado) {
        this.estado = estado;
        this.actualizadaEn = Instant.now();
    }

    public void setMotivoFallo(String motivoFallo) {
        this.motivoFallo = motivoFallo;
        this.actualizadaEn = Instant.now();
    }
}
