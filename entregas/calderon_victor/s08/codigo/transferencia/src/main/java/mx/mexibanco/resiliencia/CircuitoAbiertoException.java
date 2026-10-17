package mx.mexibanco.resiliencia;

import mx.mexibanco.compartido.ErrorRemotoException;

/**
 * Lab S08: el circuito no dejo pasar la llamada, asi que ni siquiera se intento. Para quien la recibe
 * es un 503 del servicio, igual que si no contestara, pero inmediato y sin tocar la red.
 */
public class CircuitoAbiertoException extends ErrorRemotoException {

	public CircuitoAbiertoException(String servicio, String detalle) {
		super(servicio, 503, "No se llamo a " + servicio + ": el circuito esta ABIERTO (" + detalle + ")");
	}
}
