package mx.mexibanco.transferencia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import mx.mexibanco.clientes.CuentaCliente;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/transferencias")
public class TransferenciaController {

	private final TransferenciaService transferencias;
	private final CuentaCliente cuentas;

	public TransferenciaController(TransferenciaService transferencias, CuentaCliente cuentas) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
	}

	public record SolicitudTransferencia(@NotBlank String claveOrigen, @NotBlank String claveDestino, @NotNull @Positive BigDecimal monto) {
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Transferencia transferir(@Valid @RequestBody SolicitudTransferencia solicitud) {
		return transferencias.transferir(solicitud.claveOrigen(), solicitud.claveDestino(), solicitud.monto());
	}

	/** S08: las transferencias en un estado o las ultimas 50. */
	@GetMapping
	public List<Transferencia> listar(@RequestParam(required = false) EstadoTransferencia estado) {
		return transferencias.listar(estado);
	}

	/**
	 * S08: estado del circuit breaker hacia cuenta, con su historial de transiciones.
	 * Por el gateway: GET http://localhost:8080/api/transferencias/circuito
	 */
	@GetMapping("/circuito")
	public Map<String, Object> circuito() {
		return cuentas.circuito().resumen();
	}

	@GetMapping("/{id}")
	public Transferencia consultar(@PathVariable Long id) {
		return transferencias.consultar(id);
	}
}
