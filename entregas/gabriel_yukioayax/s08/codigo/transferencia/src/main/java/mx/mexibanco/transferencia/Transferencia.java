package mx.mexibanco.transferencia;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class Transferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String claveOrigen;
    private String claveDestino;
    private BigDecimal monto;
    
    // ESTE ERA EL QUE FALTABA
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    private EstadoTransferencia estado;

    public Transferencia() {
    }

    public Transferencia(String claveOrigen, String claveDestino, BigDecimal monto) {
        this.claveOrigen = claveOrigen;
        this.claveDestino = claveDestino;
        this.monto = monto;
        this.fecha = LocalDateTime.now(); // Se genera la fecha al crear
        this.estado = EstadoTransferencia.INICIADA;
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getClaveOrigen() { return claveOrigen; }
    public void setClaveOrigen(String claveOrigen) { this.claveOrigen = claveOrigen; }

    public String getClaveDestino() { return claveDestino; }
    public void setClaveDestino(String claveDestino) { this.claveDestino = claveDestino; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public EstadoTransferencia getEstado() { return estado; }
    public void setEstado(EstadoTransferencia estado) { this.estado = estado; }
}