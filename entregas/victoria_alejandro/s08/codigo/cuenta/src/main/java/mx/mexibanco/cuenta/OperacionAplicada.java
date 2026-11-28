package mx.mexibanco.cuenta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un abono que ya se aplico, por su clave de idempotencia. Se guarda en la MISMA transaccion local
 * que mueve el saldo: o quedan los dos o ninguno. Si llega otra peticion con la misma clave, el
 * abono no se repite. La clave primaria impide que dos peticiones simultaneas la apliquen dos veces.
 */
@Entity
public class OperacionAplicada {

	@Id
	@Column(length = 100)
	private String clave;

	@Column(nullable = false, length = 18)
	private String clabe;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal monto;

	@Column(nullable = false)
	private Instant fecha;

	protected OperacionAplicada() {
	}

	public OperacionAplicada(String clave, String clabe, BigDecimal monto) {
		this.clave = clave;
		this.clabe = clabe;
		this.monto = monto;
		this.fecha = Instant.now();
	}

	/** La misma clave debe venir con la misma operacion; si no, es un error del cliente. */
	public boolean esLaMisma(String clabe, BigDecimal monto) {
		return this.clabe.equals(clabe) && this.monto.compareTo(monto) == 0;
	}

	public String getClave() {
		return clave;
	}

	public String getClabe() {
		return clabe;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public Instant getFecha() {
		return fecha;
	}
}
