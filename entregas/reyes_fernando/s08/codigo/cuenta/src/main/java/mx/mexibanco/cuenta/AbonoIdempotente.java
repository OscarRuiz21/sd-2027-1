package mx.mexibanco.cuenta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Registro persistente de una compensacion; comparte transaccion con el saldo. */
@Entity
@Table(name = "abonos_idempotentes")
public class AbonoIdempotente {

	@Id
	@Column(nullable = false, length = 120)
	private String llave;

	@Column(nullable = false, length = 18)
	private String clabe;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal monto;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal saldoResultante;

	@Column(nullable = false)
	private Instant fecha;

	protected AbonoIdempotente() {
	}

	public AbonoIdempotente(String llave, String clabe, BigDecimal monto, BigDecimal saldoResultante) {
		this.llave = llave;
		this.clabe = clabe;
		this.monto = monto;
		this.saldoResultante = saldoResultante;
		this.fecha = Instant.now();
	}

	public String getClabe() {
		return clabe;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public BigDecimal getSaldoResultante() {
		return saldoResultante;
	}
}
