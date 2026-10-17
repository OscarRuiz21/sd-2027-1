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
	// Las filas de la version S07 no tenian estado; todas las que se guardaban eran exitosas.
	// El DEFAULT permite agregar la columna NOT NULL sin borrar ni editar esas filas antiguas.
	@Column(nullable = false, length = 32, columnDefinition = "varchar(32) default 'COMPLETADA'")
	private EstadoTransferencia estado;

	@Column(length = 500)
	private String detalle;

	protected Transferencia() {
	}

	public Transferencia(String claveOrigen, String claveDestino, BigDecimal monto) {
		this.claveOrigen = claveOrigen;
		this.claveDestino = claveDestino;
		this.monto = monto;
		this.fecha = Instant.now();
		this.estado = EstadoTransferencia.INICIADA;
	}

	public void avanzar(EstadoTransferencia nuevoEstado, String nuevoDetalle) {
		this.estado = nuevoEstado;
		this.detalle = nuevoDetalle;
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

	public EstadoTransferencia getEstado() {
		return estado;
	}

	public String getDetalle() {
		return detalle;
	}
}
