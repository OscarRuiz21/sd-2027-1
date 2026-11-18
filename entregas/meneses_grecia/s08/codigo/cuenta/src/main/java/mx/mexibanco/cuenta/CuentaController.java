package mx.mexibanco.cuenta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Capa web: traduce HTTP a llamadas al servicio y nada mas. Sin reglas de negocio aqui.
 *
 * POST /cuentas y GET /cuentas/{clabe} son los publicos de siempre.
 * POST /cuentas/{clabe}/cargos y /abonos son NUEVOS en v06a y son internos: los usan transferencia
 * y spei, que en el monolito llamaban a CuentaService.cargar/abonar como metodo. Nadie los protege
 * todavia (cualquiera con curl puede abonarse dinero): eso lo resuelve el gateway, que no los
 * expondra hacia afuera, y la seguridad que llega despues.
 */
@RestController
@RequestMapping("/cuentas")
public class CuentaController {

	private final CuentaService cuentas;

	public CuentaController(CuentaService cuentas) {
		this.cuentas = cuentas;
	}

	public record AltaCuenta(@NotBlank String clabe, @NotBlank String titular, @NotNull @PositiveOrZero BigDecimal saldoInicial) {
	}

	public record Monto(@NotNull @Positive BigDecimal monto) {
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Cuenta abrir(@Valid @RequestBody AltaCuenta datos) {
		return cuentas.abrir(datos.clabe(), datos.titular(), datos.saldoInicial());
	}

	@GetMapping("/{clabe}")
	public Cuenta consultar(@PathVariable String clabe) {
		return cuentas.consultar(clabe);
	}

	/**
	 * Interno: resta el monto. 404 si la CLABE no existe, 422 si no alcanza el saldo.
	 * S08: con Idempotency-Key, repetir la misma peticion no vuelve a restar.
	 */
	@PostMapping("/{clabe}/cargos")
	public Cuenta cargar(@PathVariable String clabe, @Valid @RequestBody Monto cargo,
						 @RequestHeader(value = "Idempotency-Key", required = false) String llave) {
		return cuentas.cargar(clabe, cargo.monto(), llave);
	}

	/** Interno: suma el monto. 404 si la CLABE no existe. S08: idempotente con Idempotency-Key. */
	@PostMapping("/{clabe}/abonos")
	public Cuenta abonar(@PathVariable String clabe, @Valid @RequestBody Monto abono,
						 @RequestHeader(value = "Idempotency-Key", required = false) String llave) {
		return cuentas.abonar(clabe, abono.monto(), llave);
	}

	/** Interno (S08): 200 si la operacion con esa llave ya se aplico, 404 si no. Lo usa el orquestador. */
	@GetMapping("/operaciones/{llave}")
	public OperacionAplicada operacion(@PathVariable String llave) {
		return cuentas.operacion(llave);
	}
}
