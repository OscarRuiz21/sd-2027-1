package mx.mexibanco.transferencia;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Una fila por transferencia. Los saldos y asientos viven en sus propios servicios. */
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

	@Enumerated(EnumType.STRING)
	@Column(length = 40)
	private EstadoTransferencia estado;

	@Column(nullable = false)
	private Instant fecha;

	// NULO en transferencias antiguas: no reintentarlas sin conocer su historial.
	@Column(length = 120)
	private String llaveCompensacion;

	private Integer intentosCompensacion;

	private Instant proximoReintento;

	protected Transferencia() {
	}

	public Transferencia(String claveOrigen, String claveDestino, BigDecimal monto) {
		this.claveOrigen = claveOrigen;
		this.claveDestino = claveDestino;
		this.monto = monto;
		this.estado = EstadoTransferencia.INICIADA;
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

	public EstadoTransferencia getEstado() {
		return estado;
	}

	public String getLlaveCompensacion() {
		return llaveCompensacion;
	}

	public Integer getIntentosCompensacion() {
		return intentosCompensacion;
	}

	public Instant getProximoReintento() {
		return proximoReintento;
	}

	public void cambiarEstado(EstadoTransferencia nuevoEstado) {
		this.estado = nuevoEstado;
	}

	/** La misma llave se conserva en todos los reintentos, incluso tras un reinicio. */
	public void prepararCompensacion(String llave, Instant ahora) {
		if (llaveCompensacion == null) {
			this.llaveCompensacion = llave;
			this.intentosCompensacion = 0;
		}
		// Si el proceso cae durante la llamada, se puede recuperar tras un margen seguro.
		this.proximoReintento = ahora.plusSeconds(60);
	}

	/** 10, 20, 40, 80, 160, 300... segundos; nunca se reintenta a ciegas. */
	public long programarReintento(Instant ahora) {
		this.intentosCompensacion = (intentosCompensacion == null ? 0 : intentosCompensacion) + 1;
		long segundos = Math.min(300L, 10L << Math.min(intentosCompensacion - 1, 5));
		this.proximoReintento = ahora.plusSeconds(segundos);
		return segundos;
	}
}
