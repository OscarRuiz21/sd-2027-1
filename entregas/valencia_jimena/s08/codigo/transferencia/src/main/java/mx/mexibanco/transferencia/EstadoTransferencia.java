package mx.mexibanco.transferencia;

public enum EstadoTransferencia {
        INICIADA,                 // se registró, todavía no se mueve dinero
        CARGADA,                  // ya se le quitó el dinero al origen
        ABONADA,                  // ya se le sumó al destino
        COMPLETADA,               // todo salió bien
        FALLIDA,                  // el cargo falló: no se movió dinero, no hay nada que deshacer
        COMPENSADA,               // el abono falló y se devolvió el dinero al origen
        COMPENSACION_PENDIENTE    // el abono falló y la devolución también falló
}

