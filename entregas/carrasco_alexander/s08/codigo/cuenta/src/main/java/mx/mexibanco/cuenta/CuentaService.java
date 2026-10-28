
package mx.mexibanco.cuenta;

import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ConflictoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class CuentaService {

    private final CuentaRepository cuentas;
    private final MovimientoCliente movimientos;
    private final CompensacionRepository compensaciones;
	private final String clabeFalloAbono;

    
    public CuentaService(
            CuentaRepository cuentas,
            MovimientoCliente movimientos,
            CompensacionRepository compensaciones,
            @Value("${mexibanco.demo.fallar-abono-clabe:}") String clabeFalloAbono) {
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.compensaciones = compensaciones;
        this.clabeFalloAbono = clabeFalloAbono;
    }


    @Transactional
    public Cuenta abrir(String clabe, String titular, BigDecimal saldoInicial) {
        if (cuentas.existsByClabe(clabe)) {
            throw new ConflictoException(
                "Ya existe una cuenta con la CLABE " + clabe);
        }

        Cuenta cuenta = cuentas.save(
            new Cuenta(clabe, titular, saldoInicial));

        if (saldoInicial.signum() > 0) {
            movimientos.registrar(
                clabe,
                TipoMovimiento.DEPOSITO,
                saldoInicial,
                cuenta.getSaldo(),
                "saldo inicial");
        }

        return cuenta;
    }

    @Transactional(readOnly = true)
    public Cuenta consultar(String clabe) {
        return cuentas.findByClabe(clabe)
            .orElseThrow(() ->
                new NoEncontradoException(
                    "No existe la CLABE " + clabe));
    }

    @Transactional
    public Cuenta cargar(String clabe, BigDecimal monto) {
        Cuenta cuenta = consultar(clabe);

        if (cuenta.getSaldo().compareTo(monto) < 0) {
            throw new ReglaDeNegocioException(
                "Saldo insuficiente en la CLABE " + clabe);
        }

        cuenta.aplicar(monto.negate());
        return cuenta;
    }

    
    @Transactional
    public Cuenta abonar(String clabe, BigDecimal monto) {
        // Solo para la demo: rechazar abonos a la CLABE configurada.
        // Los cargos y las compensaciones siguen funcionando normalmente.
        if (clabeFalloAbono != null
                && !clabeFalloAbono.isBlank()
                && clabeFalloAbono.equals(clabe)) {
            throw new ReglaDeNegocioException(
                "Fallo controlado de abono para la CLABE " + clabe);
        }

        Cuenta cuenta = consultar(clabe);
        cuenta.aplicar(monto);
        return cuenta;
    }


    @Transactional
    public Cuenta compensar(
            String clabe,
            BigDecimal monto,
            String claveOperacion) {

        if (claveOperacion == null || claveOperacion.isBlank()) {
            throw new ReglaDeNegocioException(
                "La clave de compensacion es obligatoria");
        }

        if (monto == null || monto.signum() <= 0) {
            throw new ReglaDeNegocioException(
                "El monto de compensacion debe ser positivo");
        }

        var anterior =
            compensaciones.findByClaveOperacion(claveOperacion);

        if (anterior.isPresent()) {
            Compensacion registrada = anterior.get();

            if (!registrada.getClabe().equals(clabe)
                    || registrada.getMonto().compareTo(monto) != 0) {
                throw new ReglaDeNegocioException(
                    "La clave de compensacion ya se uso con otros datos");
            }

            return consultar(clabe);
        }

        Cuenta cuenta = consultar(clabe);

        compensaciones.saveAndFlush(
            new Compensacion(claveOperacion, clabe, monto));

        cuenta.aplicar(monto);

        return cuenta;
    }
}
