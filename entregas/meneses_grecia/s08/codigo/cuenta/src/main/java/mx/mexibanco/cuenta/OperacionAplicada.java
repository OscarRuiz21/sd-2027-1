package mx.mexibanco.cuenta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * S08: registro de un cargo o abono que llego con Idempotency-Key. La llave es la llave primaria:
 * si la misma operacion llega dos veces (un reintento de la saga, un timeout cuya respuesta se
 * perdio), la segunda vez ya existe y el saldo NO se vuelve a mover.
 *
 * Se guarda en la MISMA transaccion local que el cambio de saldo: o quedan los dos o ninguno.
 */
@Entity
public class OperacionAplicada {

	public enum Tipo {
		CARGO,
		ABONO
	}

	@Id
	@Column(length = 100)
	private String llave;

	@Column(nullable = false, length = 18)
	private String clabe;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Tipo tipo;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal monto;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal saldoResultante;

	@Column(nullable = false)
	private Instant fecha;

	protected OperacionAplicada() {
	}

	public OperacionAplicada(String llave, String clabe, Tipo tipo, BigDecimal monto, BigDecimal saldoResultante) {
		this.llave = llave;
		this.clabe = clabe;
		this.tipo = tipo;
		this.monto = monto;
		this.saldoResultante = saldoResultante;
		this.fecha = Instant.now();
	}

	/** Misma llave, pero otra cuenta, otro tipo u otro monto: es un error de quien llama, no un reintento. */
	public boolean coincideCon(String clabe, Tipo tipo, BigDecimal monto) {
		return this.clabe.equals(clabe) && this.tipo == tipo && this.monto.compareTo(monto) == 0;
	}

	public String getLlave() {
		return llave;
	}

	public String getClabe() {
		return clabe;
	}

	public Tipo getTipo() {
		return tipo;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public BigDecimal getSaldoResultante() {
		return saldoResultante;
	}

	public Instant getFecha() {
		return fecha;
	}
}
