package mx.mexibanco.mensajeria;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Para pruebas sin base: un TransactionTemplate que corre el callback tal cual, sin transaccion real. */
public final class SinTransaccion {

	private SinTransaccion() {
	}

	public static TransactionTemplate plantilla() {
		return new TransactionTemplate(new AbstractPlatformTransactionManager() {
			@Override
			protected Object doGetTransaction() {
				return new Object();
			}

			@Override
			protected void doBegin(Object transaccion, TransactionDefinition definicion) {
			}

			@Override
			protected void doCommit(DefaultTransactionStatus estado) {
			}

			@Override
			protected void doRollback(DefaultTransactionStatus estado) {
			}
		});
	}
}
