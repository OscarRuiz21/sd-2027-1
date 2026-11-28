package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CircuitBreaker;
import mx.mexibanco.clientes.CuentaCliente;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Para ver el circuito hacia cuenta desde afuera, por el gateway:
 *   GET http://localhost:8080/api/transferencias/circuito-cuenta
 * Vive bajo /transferencias para que el gateway ya lo enrute; la ruta literal le gana a /{id}.
 */
@RestController
public class CircuitoController {

	private final CuentaCliente cuentas;

	public CircuitoController(CuentaCliente cuentas) {
		this.cuentas = cuentas;
	}

	@GetMapping("/transferencias/circuito-cuenta")
	public CircuitBreaker.Instantanea circuitoCuenta() {
		return cuentas.estadoDelCircuito();
	}
}
