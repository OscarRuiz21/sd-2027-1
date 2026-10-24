package mx.mexibanco.transferencia;

/** Estados persistidos de la saga de una transferencia. */
public enum EstadoTransferencia {
    INICIADA,                // registrada, todavia no se movio dinero
    CARGADA,                 // se cargo al origen, falta abonar al destino
    COMPLETADA,              // cargo y abono hechos
    COMPENSADA,              // el abono fallo y se devolvio el dinero al origen
    COMPENSACION_PENDIENTE,  // el abono fallo y la devolucion tambien: requiere intervencion
    FALLIDA                  // fallo antes de mover dinero (nada que compensar)
}
