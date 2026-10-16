package mx.mexibanco.transferencia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import mx.mexibanco.resiliencia.CircuitoCuenta;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.net.URI;

@RestController
@RequestMapping("/transferencias")
public class TransferenciaController {
    private final TransferenciaService transferencias;
    private final CircuitoCuenta circuito;

    public TransferenciaController(TransferenciaService transferencias, CircuitoCuenta circuito) {
        this.transferencias = transferencias;
        this.circuito = circuito;
    }

    public record SolicitudTransferencia(@NotBlank @Pattern(regexp="[0-9]{18}") String claveOrigen,
        @NotBlank @Pattern(regexp="[0-9]{18}") String claveDestino,
        @NotNull @Positive @Digits(integer=17, fraction=2) BigDecimal monto) {}

    @PostMapping
    public ResponseEntity<Transferencia> transferir(@Valid @RequestBody SolicitudTransferencia solicitud) {
        Transferencia t = transferencias.transferir(solicitud.claveOrigen(), solicitud.claveDestino(), solicitud.monto());
        int codigo = switch (t.getEstado()) {
            case COMPLETADA -> 201;
            case COMPENSADA -> 409;
            case RECHAZADA -> 422;
            case NO_DISPONIBLE -> 503;
            default -> 202;
        };
        return ResponseEntity.status(codigo).location(URI.create("/api/transferencias/" + t.getId())).body(t);
    }

    @GetMapping("/{id}")
    public Transferencia consultar(@PathVariable Long id) { return transferencias.consultar(id); }

    @GetMapping("/circuito")
    public CircuitoCuenta.Vista circuito() { return circuito.consultar(); }
}
