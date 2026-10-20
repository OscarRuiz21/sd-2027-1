package mx.mexibanco.cuenta;

import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ConflictoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class CuentaService {

	private final CuentaRepository cuentas;
	private final MovimientoCliente movimientos;
	private final OperacionAplicadaRepository operaciones;

	public CuentaService(CuentaRepository cuentas, MovimientoCliente movimientos,
						 OperacionAplicadaRepository operaciones) {
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.operaciones = operaciones;
	}

	@Transactional
	public Cuenta abrir(String clabe, String titular, BigDecimal saldoInicial) {
		if (cuentas.existsByClabe(clabe)) {
			throw new ConflictoException("Ya existe una cuenta con la CLABE " + clabe);
		}
		Cuenta cuenta = cuentas.save(new Cuenta(clabe, titular, saldoInicial));
		if (saldoInicial.signum() > 0) {
			movimientos.registrar(clabe, TipoMovimiento.DEPOSITO, saldoInicial, cuenta.getSaldo(), "saldo inicial");
		}
		return cuenta;
	}

	@Transactional(readOnly = true)
	public Cuenta consultar(String clabe) {
		return cuentas.findByClabe(clabe)
			.orElseThrow(() -> new NoEncontradoException("No existe la CLABE " + clabe));
	}

	@Transactional
	public Cuenta cargar(String clabe, BigDecimal monto) {
		Cuenta cuenta = consultar(clabe);
		if (cuenta.getSaldo().compareTo(monto) < 0) {
			throw new ReglaDeNegocioException("Saldo insuficiente en la CLABE " + clabe);
		}
		cuenta.aplicar(monto.negate());
		return cuenta;
	}

	@Transactional
	public Cuenta abonar(String clabe, BigDecimal monto) {
		Cuenta cuenta = consultar(clabe);
		cuenta.aplicar(monto);
		return cuenta;
	}

	/** Con clave: si ya se aplicó, no mueve dinero y responde con la cuenta tal como está. */
	@Transactional
	public Cuenta cargar(String clabe, BigDecimal monto, String clave) {
		if (clave == null) {
			return cargar(clabe, monto);
		}
		if (operaciones.existsById(clave)) {
			return consultar(clabe);
		}
		Cuenta cuenta = cargar(clabe, monto);
		operaciones.saveAndFlush(new OperacionAplicada(clave)); // misma transacción que el saldo
		return cuenta;
	}

	@Transactional
	public Cuenta abonar(String clabe, BigDecimal monto, String clave) {
		if (clave == null) {
			return abonar(clabe, monto);
		}
		if (operaciones.existsById(clave)) {
			return consultar(clabe);
		}
		Cuenta cuenta = abonar(clabe, monto);
		operaciones.saveAndFlush(new OperacionAplicada(clave));
		return cuenta;
	}
}