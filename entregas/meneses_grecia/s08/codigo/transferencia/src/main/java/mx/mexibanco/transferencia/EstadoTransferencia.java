package mx.mexibanco.transferencia;

/**
 * S08: en que paso de la saga va cada transferencia. Se guarda en la base en CADA paso: si el
 * servicio se cae a la mitad, la fila dice donde se quedo y el recuperador sabe que hacer.
 *
 * <pre>
 * INICIADA --cargo ok--> CARGADA --abono ok--> ABONADA --asientos--> COMPLETADA
 *    |                      |
 *    | 4xx (no hay saldo)   | el abono falla
 *    v                      v
 * RECHAZADA          (compensacion: devolver el cargo)
 *                       |-- ok ---------> COMPENSADA
 *                       |-- falla ------> COMPENSACION_PENDIENTE --reintentos--> COMPENSADA
 *                                                     |
 *                                                     +--se agotan--> COMPENSACION_FALLIDA (revision manual)
 * </pre>
 */
public enum EstadoTransferencia {
	/** Fila creada; todavia no se sabe si el cargo ocurrio. */
	INICIADA,
	/** El dinero ya salio de origen y aun no llega a destino. Estado peligroso: no debe quedarse asi. */
	CARGADA,
	/** El dinero llego a destino (punto de no retorno: de aqui en adelante solo se avanza). */
	ABONADA,
	/** Termino bien: cargo, abono y asientos. */
	COMPLETADA,
	/** No se movio dinero (cuenta inexistente, saldo insuficiente o el cargo nunca se aplico). */
	RECHAZADA,
	/** El abono fallo y el cargo se devolvio a origen. */
	COMPENSADA,
	/** El abono fallo y la devolucion tampoco se pudo hacer todavia: el recuperador la reintenta. */
	COMPENSACION_PENDIENTE,
	/** Se agotaron los reintentos de la devolucion: requiere que una persona la revise. */
	COMPENSACION_FALLIDA;

	public boolean esFinal() {
		return this == COMPLETADA || this == RECHAZADA || this == COMPENSADA || this == COMPENSACION_FALLIDA;
	}
}
