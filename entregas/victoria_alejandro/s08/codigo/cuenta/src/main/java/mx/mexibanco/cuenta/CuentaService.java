package mx.mexibanco.cuenta;

import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ConflictoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Reglas de la cuenta. Es la UNICA clase que toca CuentaRepository, y este es el unico servicio
 * que abre la base "cuenta". Transferencia y SPEI mueven saldos por HTTP
 * (POST /cuentas/{clabe}/cargos y /abonos), nunca tocando la tabla.
 */
@Service
public class CuentaService {

	private final CuentaRepository cuentas;
	private final OperacionAplicadaRepository operaciones;
	private final MovimientoCliente movimientos;
	private final String fallaAbonoClabe;

	public CuentaService(CuentaRepository cuentas, OperacionAplicadaRepository operaciones,
						 MovimientoCliente movimientos,
						 @Value("${mexibanco.demo.falla-abono-clabe:}") String fallaAbonoClabe) {
		this.cuentas = cuentas;
		this.operaciones = operaciones;
		this.movimientos = movimientos;
		this.fallaAbonoClabe = fallaAbonoClabe;
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
		return abonar(clabe, monto, null);
	}

	/**
	 * Suma el monto. Con claveIdempotencia, el abono se aplica UNA sola vez aunque la peticion llegue
	 * repetida (un reintento de la saga, o una respuesta que se perdio): la segunda vez solo devuelve
	 * la cuenta. La clave se guarda en la misma transaccion que el saldo.
	 */
	@Transactional
	public Cuenta abonar(String clabe, BigDecimal monto, String claveIdempotencia) {
		simularFallaSiCorresponde(clabe);

		if (claveIdempotencia != null && !claveIdempotencia.isBlank()) {
			Optional<OperacionAplicada> previa = operaciones.findById(claveIdempotencia);
			if (previa.isPresent()) {
				if (!previa.get().esLaMisma(clabe, monto)) {
					throw new ConflictoException("La clave " + claveIdempotencia + " ya se uso para otro abono");
				}
				return consultar(clabe); // ya aplicado: no se suma otra vez
			}
			operaciones.save(new OperacionAplicada(claveIdempotencia, clabe, monto));
		}

		Cuenta cuenta = consultar(clabe);
		cuenta.aplicar(monto);
		return cuenta;
	}

	/**
	 * Solo para la demo de la saga (S12): con FALLA_ABONO_CLABE=<clabe>, todo abono a esa CLABE falla
	 * con 503 antes de tocar el saldo. Asi se prueba la compensacion con cuenta arriba. Vacia = apagado.
	 */
	private void simularFallaSiCorresponde(String clabe) {
		if (!fallaAbonoClabe.isBlank() && fallaAbonoClabe.equals(clabe)) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
				"Falla simulada: los abonos a la CLABE " + clabe + " estan deshabilitados (FALLA_ABONO_CLABE)");
		}
	}
}
