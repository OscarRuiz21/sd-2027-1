package mx.mexibanco.transferencia;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * La operacion completa, una fila por transferencia. Los dos asientos (cargo y abono) viven en
 * movimiento; aqui queda el registro de que la operacion ocurrio, con su propio id.
 *
 * Lab S08: la fila tambien es el estado de la saga. Se crea INICIADA antes de mover dinero y cambia
 * en cada paso (EstadoTransferencia); detalle dice en palabras por que esta asi.
 */
@Entity
@JsonInclude(JsonInclude.Include.NON_NULL)
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

	// ---- Lab S08: el estado de la saga ----

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private EstadoTransferencia estado;

	/** Lo que hizo fallar la saga (se escribe una vez, al empezar a compensar). */
	@Column(length = 500)
	private String motivo;

	/** El ultimo paso, en palabras: que se hizo o que falta. */
	@Column(length = 500)
	private String detalle;

	private Instant actualizada;

	/** Cuantas veces se ha intentado devolver el cargo, y cuando le toca al siguiente intento. */
	@ColumnDefault("0")
	private int intentosCompensacion;

	private Instant proximoIntento;

	/** Si dos procesos guardan la misma fila a la vez, el segundo truena en lugar de pisar al primero. */
	@Version
	private Long version;

	protected Transferencia() {
	}

	public Transferencia(String claveOrigen, String claveDestino, BigDecimal monto, Instant ahora) {
		this.claveOrigen = claveOrigen;
		this.claveDestino = claveDestino;
		this.monto = monto;
		this.fecha = ahora;
		cambiar(EstadoTransferencia.INICIADA, "Todavia no se mueve dinero", ahora);
	}

	void rechazada(String detalle, Instant ahora) {
		cambiar(EstadoTransferencia.RECHAZADA, detalle, ahora);
	}

	void cargada(BigDecimal saldoOrigen, Instant ahora) {
		cambiar(EstadoTransferencia.CARGADA, "Ya salieron " + monto + " de " + claveOrigen + " (queda en " + saldoOrigen
			+ "); falta el abono", ahora);
	}

	void abonada(BigDecimal saldoDestino, Instant ahora) {
		cambiar(EstadoTransferencia.ABONADA, "Ya llegaron " + monto + " a " + claveDestino + " (queda en " + saldoDestino
			+ "); falta el asiento del abono", ahora);
	}

	void completada(Instant ahora) {
		cambiar(EstadoTransferencia.COMPLETADA, "Cargo, abono y sus dos asientos", ahora);
	}

	void porCompensar(String motivo) {
		this.motivo = motivo;
	}

	/** Un intento mas de devolver el cargo. Se aparta la fila hasta 'apartadaHasta' mientras se hace. */
	int nuevoIntentoDeCompensacion(Instant apartadaHasta) {
		this.proximoIntento = apartadaHasta;
		return ++intentosCompensacion;
	}

	void compensacionPendiente(String detalle, Instant ahora, Instant proximoIntento) {
		this.proximoIntento = proximoIntento;
		cambiar(EstadoTransferencia.COMPENSACION_PENDIENTE, detalle, ahora);
	}

	void compensada(String detalle, Instant ahora) {
		this.proximoIntento = null;
		cambiar(EstadoTransferencia.COMPENSADA, detalle, ahora);
	}

	/** Agrega algo al detalle sin cambiar de estado (por ejemplo, un asiento que falto). */
	void anotar(String nota, Instant ahora) {
		this.detalle = recortar(detalle + ". " + nota);
		this.actualizada = ahora;
	}

	private void cambiar(EstadoTransferencia nuevo, String detalle, Instant ahora) {
		this.estado = nuevo;
		this.detalle = recortar(detalle);
		this.actualizada = ahora;
	}

	private static String recortar(String texto) {
		return texto.length() <= 500 ? texto : texto.substring(0, 497) + "...";
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

	public String getMotivo() {
		return motivo;
	}

	public String getDetalle() {
		return detalle;
	}

	public Instant getActualizada() {
		return actualizada;
	}

	@JsonInclude(JsonInclude.Include.NON_DEFAULT)
	public int getIntentosCompensacion() {
		return intentosCompensacion;
	}

	public Instant getProximoIntento() {
		return proximoIntento;
	}
}
