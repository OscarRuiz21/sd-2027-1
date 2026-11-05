
package mx.mexibanco.cuenta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/cuentas")
public class CuentaController {

    private final CuentaService cuentas;

    public CuentaController(CuentaService cuentas) {
        this.cuentas = cuentas;
    }

    public record AltaCuenta(
            @NotBlank String clabe,
            @NotBlank String titular,
            @NotNull @PositiveOrZero BigDecimal saldoInicial) {
    }

    public record Monto(
            @NotNull @Positive BigDecimal monto) {
    }

    public record SolicitudCompensacion(
            @NotNull @Positive BigDecimal monto,
            @NotBlank String claveOperacion) {
    }

    @PostMapping
    public Cuenta abrir(@Valid @RequestBody AltaCuenta alta) {
        return cuentas.abrir(
            alta.clabe(),
            alta.titular(),
            alta.saldoInicial());
    }

    @GetMapping("/{clabe}")
    public Cuenta consultar(@PathVariable String clabe) {
        return cuentas.consultar(clabe);
    }

    @PostMapping("/{clabe}/cargos")
    public Cuenta cargar(
            @PathVariable String clabe,
            @Valid @RequestBody Monto cargo) {
        return cuentas.cargar(clabe, cargo.monto());
    }

    @PostMapping("/{clabe}/abonos")
    public Cuenta abonar(
            @PathVariable String clabe,
            @Valid @RequestBody Monto abono) {
        return cuentas.abonar(clabe, abono.monto());
    }

    /**
     * Devuelve dinero a una cuenta por una compensacion de una saga.
     * La clave de operacion permite reconocer los reintentos.
     */
    @PostMapping("/{clabe}/compensaciones")
    public Cuenta compensar(
            @PathVariable String clabe,
            @Valid @RequestBody SolicitudCompensacion solicitud) {
        return cuentas.compensar(
            clabe,
            solicitud.monto(),
            solicitud.claveOperacion());
    }
}
