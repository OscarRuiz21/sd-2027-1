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
	private final OperacionAplicadaRepository operaciones;

	public CuentaService(CuentaRepository cuentas, MovimientoCliente movimientos, OperacionAplicadaRepository operaciones) {
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.operaciones = operaciones;
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
		return cargar(clabe, monto, null);
	}

	@Transactional
	public Cuenta abonar(String clabe, BigDecimal monto) {
		return abonar(clabe, monto, null);
	}

	/**
	 * S08: cargo IDEMPOTENTE. Si la llave ya se aplico, no se vuelve a restar: se devuelve la cuenta
	 * como esta. Sin llave (spei, clientes viejos) se comporta igual que antes.
	 */
	@Transactional
	public Cuenta cargar(String clabe, BigDecimal monto, String llave) {
		Cuenta repetida = yaAplicada(llave, clabe, OperacionAplicada.Tipo.CARGO, monto);
		if (repetida != null) {
			return repetida;
		}
		Cuenta cuenta = consultar(clabe);
		if (cuenta.getSaldo().compareTo(monto) < 0) {
			throw new ReglaDeNegocioException("Saldo insuficiente en la CLABE " + clabe);
		}
		cuenta.aplicar(monto.negate());
		recordar(llave, cuenta, OperacionAplicada.Tipo.CARGO, monto);
		return cuenta;
	}

	/** S08: abono IDEMPOTENTE. Es el que usa la saga para el abono y para la compensacion. */
	@Transactional
	public Cuenta abonar(String clabe, BigDecimal monto, String llave) {
		Cuenta repetida = yaAplicada(llave, clabe, OperacionAplicada.Tipo.ABONO, monto);
		if (repetida != null) {
			return repetida;
		}
		Cuenta cuenta = consultar(clabe);
		cuenta.aplicar(monto);
		recordar(llave, cuenta, OperacionAplicada.Tipo.ABONO, monto);
		return cuenta;
	}

	/** S08: el orquestador pregunta si una operacion suya ya se aplico (para no deshacer ni repetir a ciegas). */
	@Transactional(readOnly = true)
	public OperacionAplicada operacion(String llave) {
		return operaciones.findById(llave)
			.orElseThrow(() -> new NoEncontradoException("No se ha aplicado ninguna operacion con la llave " + llave));
	}

	private Cuenta yaAplicada(String llave, String clabe, OperacionAplicada.Tipo tipo, BigDecimal monto) {
		if (llave == null || llave.isBlank()) {
			return null;
		}
		return operaciones.findById(llave).map(previa -> {
			if (!previa.coincideCon(clabe, tipo, monto)) {
				throw new ConflictoException("La llave " + llave + " ya se uso para otra operacion");
			}
			return consultar(clabe);
		}).orElse(null);
	}

	/**
	 * Se guarda junto con el saldo. Si dos replicas procesan la misma llave al mismo tiempo, la
	 * llave primaria hace que una de las dos falle al confirmar y su cambio de saldo se deshace.
	 */
	private void recordar(String llave, Cuenta cuenta, OperacionAplicada.Tipo tipo, BigDecimal monto) {
		if (llave == null || llave.isBlank()) {
			return;
		}
		operaciones.save(new OperacionAplicada(llave, cuenta.getClabe(), tipo, monto, cuenta.getSaldo()));
	}
}
