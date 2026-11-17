package mx.mexibanco.cuenta;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

/**
 * La cache es una copia: si Redis falla, se registra y se sigue sin ella (un get cuenta como miss).
 * Va POR DENTRO de transactionAware(), para que tambien proteja el DEL que corre despues del commit.
 */
public class CacheTolerante implements Cache {

	private static final Logger log = LoggerFactory.getLogger(CacheTolerante.class);

	private final Cache destino;

	public CacheTolerante(Cache destino) {
		this.destino = destino;
	}

	private void fallo(String operacion, Object clave, RuntimeException e) {
		log.warn("[cache] fallo {} en '{}' clave {}: {}", operacion, destino.getName(), clave, e.toString());
	}

	@Override public String getName() { return destino.getName(); }

	@Override public Object getNativeCache() { return destino.getNativeCache(); }

	@Override
	public ValueWrapper get(Object clave) {
		try {
			return destino.get(clave);
		} catch (RuntimeException e) {
			fallo("GET", clave, e);
			return null;   // como un miss: el metodo corre y lee Postgres
		}
	}

	@Override
	public <T> T get(Object clave, Class<T> tipo) {
		try {
			return destino.get(clave, tipo);
		} catch (RuntimeException e) {
			fallo("GET", clave, e);
			return null;
		}
	}

	@Override
	public <T> T get(Object clave, Callable<T> cargador) {
		try {
			return destino.get(clave, cargador);
		} catch (ValueRetrievalException e) {
			throw e;   // fallo el cargador, no Redis
		} catch (RuntimeException e) {
			fallo("GET", clave, e);
			try {
				return cargador.call();
			} catch (Exception ex) {
				throw new ValueRetrievalException(clave, cargador, ex);
			}
		}
	}

	@Override
	public void put(Object clave, Object valor) {
		try {
			destino.put(clave, valor);
		} catch (RuntimeException e) {
			fallo("PUT", clave, e);
		}
	}

	@Override
	public ValueWrapper putIfAbsent(Object clave, Object valor) {
		try {
			return destino.putIfAbsent(clave, valor);
		} catch (RuntimeException e) {
			fallo("PUT", clave, e);
			return null;
		}
	}

	@Override
	public void evict(Object clave) {
		try {
			destino.evict(clave);
		} catch (RuntimeException e) {
			fallo("DEL", clave, e);
		}
	}

	@Override
	public boolean evictIfPresent(Object clave) {
		try {
			return destino.evictIfPresent(clave);
		} catch (RuntimeException e) {
			fallo("DEL", clave, e);
			return false;
		}
	}

	@Override
	public void clear() {
		try {
			destino.clear();
		} catch (RuntimeException e) {
			fallo("CLEAR", "*", e);
		}
	}

	@Override
	public boolean invalidate() {
		try {
			return destino.invalidate();
		} catch (RuntimeException e) {
			fallo("CLEAR", "*", e);
			return false;
		}
	}
}