package mx.mexibanco.transferencia;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * La operacion completa, una fila por transferencia, con el estado de la saga. Si el servicio se
 * cae a la mitad, "estado" dice en que paso se quedo.
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
	@Column(nullable = false, length = 30)
	private EstadoTransferencia estado = EstadoTransferencia.INICIADA;

	@Column(length = 500)
	private String detalle;

	@Column(nullable = false)
	private int intentosCompensacion;

	@Column(nullable = false)
	private Instant actualizada = Instant.now();

	protected Transferencia() {
	}

	public Transferencia(String claveOrigen, String claveDestino, BigDecimal monto) {
		this.claveOrigen = claveOrigen;
		this.claveDestino = claveDestino;
		this.monto = monto;
		this.fecha = Instant.now();
	}

	public void cambiarEstado(EstadoTransferencia nuevo, String detalle) {
		this.estado = nuevo;
		if (detalle != null) {
			this.detalle = detalle.length() > 500 ? detalle.substring(0, 500) : detalle;
		}
		this.actualizada = Instant.now();
	}

	public void registrarIntentoCompensacion() {
		this.intentosCompensacion++;
	}

	public Long getId() { return id; }
	public String getClaveOrigen() { return claveOrigen; }
	public String getClaveDestino() { return claveDestino; }
	public BigDecimal getMonto() { return monto; }
	public Instant getFecha() { return fecha; }
	public EstadoTransferencia getEstado() { return estado; }
	public String getDetalle() { return detalle; }
	public int getIntentosCompensacion() { return intentosCompensacion; }
	public Instant getActualizada() { return actualizada; }
}