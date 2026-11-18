package mx.mexibanco.transferencia;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * La operacion completa, una fila por transferencia. Los asientos (cargo, abono y, si hubo,
 * devolucion) viven en movimiento; aqui queda la operacion y, desde S08, EN QUE PASO DE LA SAGA VA.
 *
 * Las columnas nuevas de S08 son nullables a proposito: con ddl-auto=update se agregan a la tabla
 * que ya tenia filas de la S07 sin romperla.
 */
@Entity
public class Transferencia {

	private static final int LARGO_TEXTO = 500;

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

	// ---- S08: estado de la saga ----

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private EstadoTransferencia estado;

	/** Por que no se completo (la primera falla). Se conserva entre reintentos. */
	@Column(length = LARGO_TEXTO)
	private String motivo;

	/** Lo ultimo que paso (se reescribe en cada paso). */
	@Column(length = LARGO_TEXTO)
	private String detalle;

	private Integer intentosCompensacion;

	/** Cuando le toca al recuperador volver a intentar la devolucion (espera creciente). */
	private Instant proximoIntento;

	private Instant actualizada;

	/** Si ya se registro en movimiento el asiento del cargo (para que el ledger cuadre al devolver). */
	private Boolean asientoCargo;

	/** Bloqueo optimista: si el orquestador y el recuperador tocan la misma fila, gana uno. */
	@Version
	private Long version;

	protected Transferencia() {
	}

	public Transferencia(String claveOrigen, String claveDestino, BigDecimal monto) {
		this.claveOrigen = claveOrigen;
		this.claveDestino = claveDestino;
		this.monto = monto;
		this.fecha = Instant.now();
		this.estado = EstadoTransferencia.INICIADA;
		this.detalle = "Transferencia iniciada";
		this.intentosCompensacion = 0;
		this.asientoCargo = false;
		this.actualizada = this.fecha;
	}

	/** Cambia de paso y deja escrito por que. */
	public void pasarA(EstadoTransferencia nuevo, String detalle) {
		this.estado = nuevo;
		this.detalle = recortar(detalle);
		this.actualizada = Instant.now();
		if (nuevo != EstadoTransferencia.COMPENSACION_PENDIENTE) {
			this.proximoIntento = null;
		}
	}

	/** La primera falla se guarda como motivo; las siguientes solo van a detalle. */
	public void anotarMotivo(String motivo) {
		if (this.motivo == null) {
			this.motivo = recortar(motivo);
		}
	}

	/**
	 * La devolucion fallo: se programa otro intento con espera creciente (base, 2*base, 4*base...
	 * hasta maximo). Si ya se agotaron los intentos, queda para revision manual.
	 */
	public void compensacionFallo(String error, Duration base, Duration maximo, int maxIntentos) {
		int intentos = getIntentosCompensacion() + 1;
		this.intentosCompensacion = intentos;
		if (intentos >= maxIntentos) {
			pasarA(EstadoTransferencia.COMPENSACION_FALLIDA,
				"La devolucion fallo " + intentos + " veces; requiere revision manual. Ultimo error: " + error);
			return;
		}
		long factor = 1L << Math.min(intentos - 1, 20);
		Duration espera = base.multipliedBy(factor);
		if (espera.compareTo(maximo) > 0) {
			espera = maximo;
		}
		pasarA(EstadoTransferencia.COMPENSACION_PENDIENTE,
			"No se pudo devolver el cargo (intento " + intentos + "): " + error
				+ ". Siguiente intento en " + espera.toMillis() + " ms");
		this.proximoIntento = Instant.now().plus(espera);
	}

	public void marcarAsientoCargo() {
		this.asientoCargo = true;
	}

	// Llaves de idempotencia que se mandan a cuenta. Derivadas del id: un reintento manda la misma.

	public String llaveCargo() {
		return "transferencia-" + id + "-cargo";
	}

	public String llaveAbono() {
		return "transferencia-" + id + "-abono";
	}

	public String llaveDevolucion() {
		return "transferencia-" + id + "-devolucion";
	}

	private static String recortar(String texto) {
		if (texto == null || texto.length() <= LARGO_TEXTO) {
			return texto;
		}
		return texto.substring(0, LARGO_TEXTO - 3) + "...";
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

	public int getIntentosCompensacion() {
		return intentosCompensacion == null ? 0 : intentosCompensacion;
	}

	public Instant getProximoIntento() {
		return proximoIntento;
	}

	public Instant getActualizada() {
		return actualizada;
	}

	@JsonIgnore
	public boolean isAsientoCargo() {
		return Boolean.TRUE.equals(asientoCargo);
	}

	@JsonIgnore
	public Long getVersion() {
		return version;
	}
}
