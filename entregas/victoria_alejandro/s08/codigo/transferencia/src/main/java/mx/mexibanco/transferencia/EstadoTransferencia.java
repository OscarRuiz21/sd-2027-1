package mx.mexibanco.transferencia;

/**
 * En que paso de la saga va cada transferencia. Se guarda en la tabla despues de cada paso: si el
 * servicio se cae a la mitad, la base dice donde se quedo.
 *
 *   INICIADA -> CARGADA -> ABONADA -> COMPLETADA          (camino feliz)
 *   INICIADA -> RECHAZADA                                 (el cargo fallo: no se movio dinero)
 *   CARGADA  -> COMPENSADA                                (el abono fallo y se devolvio el monto)
 *   CARGADA  -> COMPENSACION_PENDIENTE -> COMPENSADA      (la devolucion tambien fallo; la termina el reintento)
 */
public enum EstadoTransferencia {
	INICIADA,
	RECHAZADA,
	CARGADA,
	ABONADA,
	COMPLETADA,
	COMPENSADA,
	COMPENSACION_PENDIENTE
}
