package mx.mexibanco.transferencia;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * La operacion completa, una fila por transferencia. Los dos asientos (cargo y abono) viven en
 * movimiento; aqui queda el registro de que la operacion ocurrio, con su propio id.
 */
@Entity
public class Transferencia {

	public enum Estado { INICIADA, CARGO_PENDIENTE, CARGADA, ABONO_PENDIENTE, ABONADA, COMPLETADA, COMPENSACION_PENDIENTE, DEVUELTA, COMPENSADA, REGISTRO_PENDIENTE, REVISION_MANUAL }
	@Enumerated(EnumType.STRING)
	private Estado estado = Estado.INICIADA;
	@Column(length = 2000)
	private String detalle;
	public Estado getEstado() { return estado; }
	public String getDetalle() { return detalle; }
	public void cambiar(Estado estado, String detalle) { this.estado = estado; this.detalle = detalle; }

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

	protected Transferencia() {
	}

	public Transferencia(String claveOrigen, String claveDestino, BigDecimal monto) {
		this.claveOrigen = claveOrigen;
		this.claveDestino = claveDestino;
		this.monto = monto;
		this.fecha = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getClaveOrigen() {
		return claveOrigen;
	}

	public String getClaveDestino() {
		return claveDestino;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public Instant getFecha() {
		return fecha;
	}
}
