package mx.mexibanco.cuenta;

import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ConflictoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Reglas de la cuenta. Es la UNICA clase que toca CuentaRepository,
 * y este es el unico servicio que abre la base "cuenta".
 * Transferencia y SPEI mueven saldos por HTTP.
 */
@Service
public class CuentaService {

    private final CuentaRepository cuentas;
    private final MovimientoCliente movimientos;
    private final OperacionAplicadaRepository operaciones;

    public CuentaService(
            CuentaRepository cuentas,
            MovimientoCliente movimientos,
            OperacionAplicadaRepository operaciones) {
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.operaciones = operaciones;
    }

    /**
     * Crea una cuenta y registra el saldo inicial.
     * Si el servicio movimiento falla, la transaccion local se deshace.
     */
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
                "saldo inicial"
            );
        }

        return cuenta;
    }

    /**
     * Consulta el saldo real desde PostgreSQL.
     * Este metodo NO utiliza Redis porque sirve para autorizar cargos.
     */
    @Transactional(readOnly = true)
    public Cuenta consultar(String clabe) {
        return cuentas.findByClabe(clabe)
            .orElseThrow(() ->
                new NoEncontradoException(
                    "No existe la CLABE " + clabe));
    }

    /**
     * Resta dinero de una cuenta.
     * S11: elimina la copia del saldo en Redis despues del commit.
     */
    @CacheEvict(
        cacheNames = ConfiguracionCache.CUENTAS,
        key = "#clabe"
    )
    @Transactional
    public SaldoAplicado cargar(
            String clabe,
            BigDecimal monto,
            String clave) {

        Optional<SaldoAplicado> repetida =
            yaAplicada(
                clave,
                clabe,
                OperacionAplicada.Tipo.CARGO,
                monto
            );

        if (repetida.isPresent()) {
            return repetida.get();
        }

        Cuenta cuenta = consultar(clabe);

        if (cuenta.getSaldo().compareTo(monto) < 0) {
            throw new ReglaDeNegocioException(
                "Saldo insuficiente en la CLABE " + clabe);
        }

        cuenta.aplicar(monto.negate());

        return recordar(
            clave,
            cuenta,
            OperacionAplicada.Tipo.CARGO,
            monto
        );
    }

    /**
     * Suma dinero a una cuenta.
     * S11: elimina la copia del saldo en Redis despues del commit.
     */
    @CacheEvict(
        cacheNames = ConfiguracionCache.CUENTAS,
        key = "#clabe"
    )
    @Transactional
    public SaldoAplicado abonar(
            String clabe,
            BigDecimal monto,
            String clave) {

        Optional<SaldoAplicado> repetida =
            yaAplicada(
                clave,
                clabe,
                OperacionAplicada.Tipo.ABONO,
                monto
            );

        if (repetida.isPresent()) {
            return repetida.get();
        }

        Cuenta cuenta = consultar(clabe);
        cuenta.aplicar(monto);

        return recordar(
            clave,
            cuenta,
            OperacionAplicada.Tipo.ABONO,
            monto
        );
    }

    /**
     * Comprueba si una operacion ya se realizo con la misma
     * Idempotency-Key, para evitar duplicar movimientos.
     */
    private Optional<SaldoAplicado> yaAplicada(
            String clave,
            String clabe,
            OperacionAplicada.Tipo tipo,
            BigDecimal monto) {

        if (clave == null || clave.isBlank()) {
            return Optional.empty();
        }

        return operaciones.findByClave(clave).map(previa -> {
            if (!previa.esLaMisma(clabe, tipo, monto)) {
                throw new ConflictoException(
                    "La Idempotency-Key " + clave +
                    " ya se uso para otra operacion"
                );
            }

            return new SaldoAplicado(
                previa.getClabe(),
                consultar(clabe).getTitular(),
                previa.getSaldoResultante()
            );
        });
    }

    /**
     * Guarda la clave de idempotencia dentro de la misma
     * transaccion que modifica el saldo.
     */
    private SaldoAplicado recordar(
            String clave,
            Cuenta cuenta,
            OperacionAplicada.Tipo tipo,
            BigDecimal monto) {

        if (clave != null && !clave.isBlank()) {
            operaciones.save(
                new OperacionAplicada(
                    clave,
                    cuenta.getClabe(),
                    tipo,
                    monto,
                    cuenta.getSaldo()
                )
            );
        }

        return SaldoAplicado.de(cuenta);
    }
}
