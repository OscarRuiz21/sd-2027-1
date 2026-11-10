package mx.mexibanco.transferencia;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TransferenciaService {

    @Autowired
    private TransferenciaRepository transferenciaRepository;

    // TODO: Inyecta aquí tu RestTemplate, WebClient o FeignClient para hacer las llamadas a los otros microservicios

    /**
     * 1. EL ENUM DE ESTADOS (Solicitado en la Parte 3 de la retroalimentación)
     * Define claramente cada paso de la Saga.
     */
    public enum EstadoTransferencia {
        INICIADA,
        CARGADA,
        ABONADA,
        COMPLETADA,
        COMPENSANDO,
        COMPENSADA,
        COMPENSACION_PENDIENTE,
        RECHAZADA 
    }

    /**
     * Método principal que orquesta la transferencia (La Saga)
     */
    public Transferencia transferir(Transferencia transferencia) {
        transferencia.setEstado(EstadoTransferencia.INICIADA.name());
        transferenciaRepository.save(transferencia);

        try {
            // 1. Descontar el monto de la cuenta de origen
            realizarCargo(transferencia);
            transferencia.setEstado(EstadoTransferencia.CARGADA.name());
            transferenciaRepository.save(transferencia);

            // 2. Intentar depositar el monto en la cuenta destino
            realizarAbono(transferencia);
            transferencia.setEstado(EstadoTransferencia.ABONADA.name());
            transferenciaRepository.save(transferencia);

            // 3. Confirmar la transferencia
            transferencia.setEstado(EstadoTransferencia.COMPLETADA.name());
            transferenciaRepository.save(transferencia);

        } catch (Exception e) {
            // Si el abono falla después del cargo, se inicia la compensación
            if (transferencia.getEstado().equals(EstadoTransferencia.CARGADA.name())) {
                transferencia.setEstado(EstadoTransferencia.COMPENSANDO.name());
                transferencia.setDetalle(e.getMessage());
                transferenciaRepository.save(transferencia);
                
                compensarTransferencia(transferencia);
            } else {
                // Si falla ANTES del cargo (ej. cuenta no existe, saldo insuficiente)
                rechazoDefinitivo(transferencia, "Fallo antes del cargo: " + e.getMessage());
            }
        }
        return transferencia;
    }

    /**
     * 2. MÉTODO COMPENSAR (Solicitado en la Parte 3 y corregido para la Parte 4)
     * Contiene la lógica idempotente para devolver el dinero si el abono falla.
     */
    public void compensarTransferencia(Transferencia transferencia) {
        try {
            // Idempotencia: Verificar si ya se devolvió el dinero antes de intentarlo de nuevo
            if (yaExisteDevolucion(transferencia.getId())) {
                transferencia.setEstado(EstadoTransferencia.COMPENSADA.name());
                transferenciaRepository.save(transferencia);
                return;
            }

            // Llamada al microservicio de cuenta para regresar el dinero
            devolverMontoCuentaOrigen(transferencia);
            
            // Llamada al microservicio de movimientos para registrar la devolución
            registrarMovimientoDevolucion(transferencia);

            transferencia.setEstado(EstadoTransferencia.COMPENSADA.name());
            transferencia.setDetalle("Saldo devuelto y movimiento de devolución registrado.");

        } catch (Exception e) {
            // Si la cuenta o el movimiento se caen durante la compensación, se queda pendiente
            transferencia.setEstado(EstadoTransferencia.COMPENSACION_PENDIENTE.name());
            transferencia.setDetalle("No se pudo confirmar la compensación: " + e.getMessage());
        } finally {
            transferenciaRepository.save(transferencia);
        }
    }

    /**
     * 3. MÉTODO RECHAZO DEFINITIVO (Solicitado en la Parte 3)
     * Se usa cuando la operación no puede ni siquiera iniciar (ej. sin fondos o cuenta inválida).
     */
    public void rechazoDefinitivo(Transferencia transferencia, String motivo) {
        transferencia.setEstado(EstadoTransferencia.RECHAZADA.name());
        transferencia.setDetalle(motivo);
        transferenciaRepository.save(transferencia);
    }

    // ========================================================================
    // MÉTODOS AUXILIARES PARA LAS LLAMADAS HTTP (Adapta según tu código)
    // ========================================================================

    private void realizarCargo(Transferencia t) {
        // Lógica HTTP para restar saldo al origen
    }

    private void realizarAbono(Transferencia t) {
        // Simulador de fallo controlado por variable de entorno (Parte 1)
        String clabeFallida = System.getenv("FALLAR_ABONO_CLABE");
        if (clabeFallida != null && !clabeFallida.isBlank() && clabeFallida.equals(t.getClaveDestino())) {
            throw new RuntimeException("Fallo de prueba: abono rechazado para la CLABE " + t.getClaveDestino());
        }
        
        // Lógica HTTP para sumar saldo al destino
    }

    private void devolverMontoCuentaOrigen(Transferencia t) {
        // Lógica HTTP para sumar saldo de vuelta al origen
    }

    private void registrarMovimientoDevolucion(Transferencia t) {
        /*
         * ¡IMPORTANTE PARA CORREGIR LA PARTE 4! 
         * Aquí ocurre el error 500. Asegúrate de que el JSON que envías 
         * tenga un campo "tipo" válido para tu base de datos de 'movimiento'.
         * Si mandas "DEVOLUCION" y no existe en ese servicio, explotará.
         * Cambialo temporalmente a "DEPOSITO" o asegúrate de dar de alta "DEVOLUCION"
         * en el servicio de movimientos.
         */
        
        // Ejemplo de payload a enviar:
        // {
        //    "clabe_cuenta": t.getClaveOrigen(),
        //    "tipo": "DEPOSITO",  <-- REVISA ESTO
        //    "monto": t.getMonto(),
        //    "referencia": "Devolución por transferencia fallida " + t.getId()
        // }
        
        // Lógica HTTP POST a /api/movimientos
    }

    private boolean yaExisteDevolucion(Long transferenciaId) {
        // Lógica HTTP para consultar si ya existe el movimiento de devolución
        // Retorna true si ya existe, false si no.
        return false;
    }
}