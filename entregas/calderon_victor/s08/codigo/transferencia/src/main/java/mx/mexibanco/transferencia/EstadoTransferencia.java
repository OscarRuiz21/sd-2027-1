package mx.mexibanco.transferencia;

/**
 * Lab S08: en que paso de la saga va cada transferencia. Se guarda en la tabla en cada paso, asi que
 * si el servicio se cae a la mitad la base dice hasta donde llego.
 *
 *   INICIADA -> CARGADA -> ABONADA -> COMPLETADA         camino feliz
 *   INICIADA -> RECHAZADA                                fallo antes del cargo: no hay nada que deshacer
 *   CARGADA  -> COMPENSADA                               fallo el abono y se devolvio el cargo
 *   CARGADA  -> COMPENSACION_PENDIENTE -> COMPENSADA     la devolucion tambien fallo: se reintenta sola
 */
public enum EstadoTransferencia {
	/** Ya tiene id en la base; todavia no se mueve dinero. */
	INICIADA,
	/** Fallo antes del cargo (no existe una cuenta, no alcanza el saldo, cuenta no contesta). Final. */
	RECHAZADA,
	/** El dinero ya salio de origen. Si el abono falla, hay que devolverlo. */
	CARGADA,
	/** El dinero ya llego a destino. Desde aqui ya no se compensa: lo que falte se termina. */
	ABONADA,
	/** Cargo, abono y sus dos asientos. Final. */
	COMPLETADA,
	/** El abono fallo y se le devolvio el monto a origen. Final. */
	COMPENSADA,
	/** El abono fallo y la devolucion tambien: ReintentoDeCompensaciones la sigue intentando. */
	COMPENSACION_PENDIENTE
}
