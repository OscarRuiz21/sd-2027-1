package mx.mexibanco.clientes;

/**
 * Copia del enum del servicio movimiento. Si movimiento agrega un tipo, esta copia no se entera
 * sola: es el precio de no compartir codigo entre servicios (ver README).
 *
 * TRANSFERENCIA_REVERTIDA (S12) hay que agregarlo TAMBIEN en el enum del servicio movimiento,
 * o movimiento rechazara el asiento del reverso.
 */
public enum TipoMovimiento {
	DEPOSITO,
	TRANSFERENCIA_ENVIADA,
	TRANSFERENCIA_RECIBIDA,
	SPEI_ENVIADO,
	TRANSFERENCIA_REVERTIDA
}
