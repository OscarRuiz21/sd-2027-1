package mx.mexibanco.transferencia;

import org.springframework.stereotype.Component;
import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransferenciaService {

    private final TransferenciaRepository transferencias;
    private final CuentaCliente cuentas;
    private final MovimientoCliente movimientos;
    private final NotificacionCliente notificaciones;
    private final long pausaEntreCargoYAbonoMs;
    private final String clabeFallaAbono;
    private final CuentaCircuitBreaker circuitBreaker;

    public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
                                MovimientoCliente movimientos, NotificacionCliente notificaciones,
                                @Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
                                @Value("${mexibanco.demo.clabe-falla-abono:}") String clabeFallaAbono, 
                                CuentaCircuitBreaker circuitBreaker) {
        
        this.circuitBreaker = circuitBreaker;
        this.transferencias = transferencias;
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.notificaciones = notificaciones;
        this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
        this.clabeFallaAbono = clabeFallaAbono;
    }

    public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
        if (claveOrigen.equals(claveDestino)) {
            throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
        }

        // --- AQUI ENTRA EL CIRCUIT BREAKER ---
        circuitBreaker.comprobar();

        try {
            cuentas.consultar(claveOrigen);
            cuentas.consultar(claveDestino);
            circuitBreaker.registrarExito();
        } catch (Exception e) {
            circuitBreaker.registrarFalla();
            throw new RuntimeException("Error al consultar cuentas: " + e.getMessage());
        }
        // -------------------------------------

        Transferencia transferencia = new Transferencia(claveOrigen, claveDestino, monto);
        transferencia.setEstado("INICIADA");
        transferencia = transferencias.save(transferencia);

        try {
            CuentaRemota origen = cuentas.cargar(claveOrigen, monto);
            transferencia.setEstado("CARGADA");
            transferencias.save(transferencia);

            pausaDeDemo();

            if (claveDestino.equals(clabeFallaAbono)) {
                throw new RuntimeException("Falla simulada: Abono rechazado por variable de entorno");
            }

            CuentaRemota destino = cuentas.abonar(claveDestino, monto);
            transferencia.setEstado("ABONADA");
            transferencias.save(transferencia);

            MovimientoRemoto cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
                    monto.negate(), origen.saldo(), "a " + destino.clabe());
            MovimientoRemoto abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
                    monto, destino.saldo(), "de " + origen.clabe());

            transferencia.setEstado("COMPLETADA");
            transferencias.save(transferencia);

            notificaciones.notificar(cargo);
            notificaciones.notificar(abono);
            
            return transferencia;

        } catch (Exception e) {
            if ("CARGADA".equals(transferencia.getEstado())) {
                try {
                    CuentaRemota origenDevolucion = cuentas.abonar(claveOrigen, monto);
                    movimientos.registrar(claveOrigen, TipoMovimiento.TRANSFERENCIA_RECIBIDA, 
                            monto, origenDevolucion.saldo(), "DEVOLUCIÓN por falla hacia " + claveDestino);

                    transferencia.setEstado("COMPENSADA");
                    transferencias.save(transferencia);

                    throw new ReglaDeNegocioException("Falló el abono destino. Se compensó regresando el dinero a origen. Error original: " + e.getMessage());

                } catch (Exception exCompensacion) {
                    transferencia.setEstado("COMPENSACION_PENDIENTE");
                    transferencias.save(transferencia);
                    throw new RuntimeException("ERROR CRÍTICO: Falló abono y falló compensación. Dinero en el aire.", exCompensacion);
                }
            }

            transferencia.setEstado("FALLIDA");
            transferencias.save(transferencia);
            throw new ReglaDeNegocioException("La transferencia falló antes de mover dinero: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Transferencia consultar(Long id) {
        return transferencias.findById(id)
                .orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
    }

    private void pausaDeDemo() {
        if (pausaEntreCargoYAbonoMs <= 0) {
            return;
        }
        try {
            Thread.sleep(pausaEntreCargoYAbonoMs);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
        }
    }
}

// Nota: Le quité el "public" para que pueda vivir en el mismo archivo sin problemas.
@Component
class CuentaCircuitBreaker {
    private enum Estado { CERRADO, ABIERTO, SEMIABIERTO }
    
    private Estado estado = Estado.CERRADO;
    private int fallas = 0;
    private final int MAX_FALLAS = 3;
    private long tiempoApertura = 0;
    private final long TIEMPO_ESPERA_MS = 10000;


    public synchronized void comprobar() {
        if (estado == Estado.ABIERTO) {
            if (System.currentTimeMillis() - tiempoApertura > TIEMPO_ESPERA_MS) {
                estado = Estado.SEMIABIERTO;
                System.out.println("CIRCUIT BREAKER: SEMIABIERTO (Dejando pasar una petición de prueba...)");
            } else {
                throw new RuntimeException("CIRCUIT BREAKER ABIERTO: Fallo rápido. El servicio de cuenta está caído.");
            }
        }
    }

    public synchronized void registrarExito() {
        if (estado != Estado.CERRADO) {
            System.out.println("CIRCUIT BREAKER: CERRADO (Servicio recuperado)");
        }
        estado = Estado.CERRADO;
        fallas = 0;
    }

    public synchronized void registrarFalla() {
        fallas++;
        System.out.println("CIRCUIT BREAKER: Falla registrada (" + fallas + "/" + MAX_FALLAS + ")");
        if (fallas >= MAX_FALLAS && estado != Estado.ABIERTO) {
            estado = Estado.ABIERTO;
            tiempoApertura = System.currentTimeMillis();
            System.out.println("CIRCUIT BREAKER: ABIERTO (Cortando llamadas a cuenta por " + (TIEMPO_ESPERA_MS/1000) + " segundos)");
        }
    }
}