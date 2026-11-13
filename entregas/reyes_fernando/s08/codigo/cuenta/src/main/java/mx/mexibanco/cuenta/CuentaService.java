package mx.mexibanco.cuenta;

import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ConflictoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Reglas de la cuenta. Es la UNICA clase que toca CuentaRepository, y este es el unico servicio
 * que abre la base "cuenta". Transferencia y SPEI mueven saldos por HTTP
 * (POST /cuentas/{clabe}/cargos y /abonos), nunca tocando la tabla.
 */
@Service
public class CuentaService {

	private final CuentaRepository cuentas;
	private final MovimientoCliente movimientos;
	private final AbonoIdempotenteRepository abonosIdempotentes;

	public CuentaService(CuentaRepository cuentas, MovimientoCliente movimientos,
			AbonoIdempotenteRepository abonosIdempotentes) {
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.abonosIdempotentes = abonosIdempotentes;
	}

	/**
	 * Si el servicio movimiento falla, la excepcion sale de aqui y la transaccion LOCAL se deshace:
	 * la cuenta no se crea. Lo contrario no esta cubierto: si movimiento guardo el asiento y luego
	 * falla el commit de aqui, queda un deposito sin cuenta. Ya no hay una transaccion que abarque a los dos.
	 */
	@Transactional
	public Cuenta abrir(String clabe, String titular, BigDecimal saldoInicial) {
		if (cuentas.existsByClabe(clabe)) {
			throw new ConflictoException("Ya existe una cuenta con la CLABE " + clabe);
		}
		Cuenta cuenta = cuentas.save(new Cuenta(clabe, titular, saldoInicial));
		// El saldo es cache de los movimientos: si la cuenta nace con dinero, el ledger lo registra
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

	/**
	 * Resta el monto. En el monolito participaba en la transaccion de quien la llamaba; ahora cada
	 * cargo se confirma solo, en cuanto responde. Si despues falla el abono, este cargo ya ocurrio.
	 */
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

	/**
	 * La compensacion usa una llave estable, no un abono ordinario.
	 * El bloqueo de la cuenta serializa las peticiones de todas las replicas.
	 * El registro y la variacion del saldo se confirman en la MISMA transaccion local.
	 */
	@Transactional
	public AbonoConfirmado abonarCompensacion(String clabe, BigDecimal monto, String llave) {
		if (llave == null || llave.isBlank() || llave.length() > 120) {
			throw new ReglaDeNegocioException("Idempotency-Key debe tener entre 1 y 120 caracteres");
		}

		Cuenta cuenta = cuentas.bloquearPorClabe(clabe)
			.orElseThrow(() -> new NoEncontradoException("No existe la CLABE " + clabe));

		var anterior = abonosIdempotentes.findById(llave);
		if (anterior.isPresent()) {
			AbonoIdempotente registrado = anterior.get();
			if (!registrado.getClabe().equals(clabe) || registrado.getMonto().compareTo(monto) != 0) {
				throw new ConflictoException("La llave ya se utilizo con otra cuenta o monto");
			}
			// Responde el saldo del PRIMER abono, no el saldo que tenga la cuenta hoy.
			return new AbonoConfirmado(clabe, cuenta.getTitular(), registrado.getSaldoResultante());
		}

		cuenta.aplicar(monto);
		abonosIdempotentes.saveAndFlush(new AbonoIdempotente(llave, clabe, monto, cuenta.getSaldo()));
		return new AbonoConfirmado(clabe, cuenta.getTitular(), cuenta.getSaldo());
	}

	public record AbonoConfirmado(String clabe, String titular, BigDecimal saldo) {
	}
}
