package mx.mexibanco.transferencia;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * La operacion completa, una fila por transferencia. Los dos asientos (cargo y abono) viven en
 * movimiento; aqui queda el registro de que la operacion ocurrio, con su propio id.
 *
 * Desde S12 tambien es el estado de la saga: cada paso que avanza se guarda aqui antes de dar el
 * siguiente, para que un proceso que muere a la mitad deje rastro de en que paso se quedo.
 */
@Entity
public class Transferencia {

	private static final int LARGO_MENSAJE = 500;

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
	private EstadoTransferencia estado;

	/** Por que no se completo: el error del cargo (RECHAZADA) o del abono (COMPENSADA / PENDIENTE). */
	@Column(length = LARGO_MENSAJE)
	private String motivoFalla;

	/** El ultimo error al intentar devolver el monto. Se limpia cuando la compensacion sale bien. */
	@Column(length = LARGO_MENSAJE)
	private String errorCompensacion;

	/** Saldo de origen justo despues del cargo: lo necesita el asiento del cargo si hay que revertir. */
	@Column(precision = 19, scale = 2)
	private BigDecimal saldoOrigenTrasCargo;

	@Column(nullable = false)
	private int intentosCompensacion;

	@Column(nullable = false)
	private Instant actualizada;

	protected Transferencia() {
	}

	public Transferencia(String claveOrigen, String claveDestino, BigDecimal monto) {
		this.claveOrigen = claveOrigen;
		this.claveDestino = claveDestino;
		this.monto = monto;
		this.fecha = Instant.now();
		this.estado = EstadoTransferencia.INICIADA;
		this.actualizada = this.fecha;
	}

	// --- transiciones de la saga ---

	public void rechazada(String motivo) {
		this.motivoFalla = recortar(motivo);
		cambiarA(EstadoTransferencia.RECHAZADA);
	}

	public void cargada(BigDecimal saldoOrigen) {
		this.saldoOrigenTrasCargo = saldoOrigen;
		cambiarA(EstadoTransferencia.CARGADA);
	}

	public void abonada() {
		cambiarA(EstadoTransferencia.ABONADA);
	}

	public void completada() {
		cambiarA(EstadoTransferencia.COMPLETADA);
	}

	/** El abono fallo: se anota por que. El estado lo decide la compensacion (COMPENSADA o PENDIENTE). */
	public void falloElAbono(String motivo) {
		this.motivoFalla = recortar(motivo);
		this.actualizada = Instant.now();
	}

	public void registrarIntentoDeCompensacion() {
		this.intentosCompensacion++;
		this.actualizada = Instant.now();
	}

	public void compensada() {
		this.errorCompensacion = null;
		cambiarA(EstadoTransferencia.COMPENSADA);
	}

	public void compensacionPendiente(String error) {
		this.errorCompensacion = recortar(error);
		cambiarA(EstadoTransferencia.COMPENSACION_PENDIENTE);
	}

	private void cambiarA(EstadoTransferencia nuevo) {
		this.estado = nuevo;
		this.actualizada = Instant.now();
	}

	private static String recortar(String texto) {
		if (texto == null || texto.length() <= LARGO_MENSAJE) {
			return texto;
		}
		return texto.substring(0, LARGO_MENSAJE - 3) + "...";
	}

	// --- getters ---

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

	public String getMotivoFalla() {
		return motivoFalla;
	}

	public String getErrorCompensacion() {
		return errorCompensacion;
	}

	public BigDecimal getSaldoOrigenTrasCargo() {
		return saldoOrigenTrasCargo;
	}

	public int getIntentosCompensacion() {
		return intentosCompensacion;
	}

	public Instant getActualizada() {
		return actualizada;
	}
}
