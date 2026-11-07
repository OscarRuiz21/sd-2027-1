package mx.mexibanco.cuenta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

/**
 * Reto S11: la cache es solo una copia para mostrar, asi que si Redis falla no debe tumbar a cuenta.
 * Envuelve la RedisCache y atrapa sus errores: un GET que falla cuenta como miss (se lee Postgres),
 * y un SET o DEL que falla solo se anota en el log. Va DEBAJO del TransactionAwareCacheDecorator,
 * asi el DEL sigue esperando al commit y, cuando corre, su error tambien se atrapa aqui.
 */
class CacheTolerante implements Cache {

	private static final Logger log = LoggerFactory.getLogger(CacheTolerante.class);

	private final Cache redis;

	CacheTolerante(Cache redis) {
		this.redis = redis;
	}

	@Override
	public String getName() {
		return redis.getName();
	}

	@Override
	public Object getNativeCache() {
		return redis.getNativeCache();
	}

	@Override
	public ValueWrapper get(Object key) {
		try {
			return redis.get(key);
		} catch (RuntimeException falla) {
			avisar("GET", key, falla);
			return null; // como un miss: corre el metodo y lee Postgres
		}
	}

	@Override
	public <T> T get(Object key, Class<T> type) {
		try {
			return redis.get(key, type);
		} catch (RuntimeException falla) {
			avisar("GET", key, falla);
			return null;
		}
	}

	@Override
	public <T> T get(Object key, Callable<T> valueLoader) {
		// solo lo usa @Cacheable(sync = true), que en cuenta no se usa
		return redis.get(key, valueLoader);
	}

	@Override
	public void put(Object key, Object value) {
		try {
			redis.put(key, value);
		} catch (RuntimeException falla) {
			avisar("SET", key, falla);
		}
	}

	@Override
	public void evict(Object key) {
		try {
			redis.evict(key);
		} catch (RuntimeException falla) {
			avisar("DEL", key, falla);
		}
	}

	@Override
	public void clear() {
		try {
			redis.clear();
		} catch (RuntimeException falla) {
			avisar("CLEAR", "*", falla);
		}
	}

	private void avisar(String operacion, Object key, RuntimeException falla) {
		log.warn("[cache] fallo {} {}::{} ({}: {}); sigo sin cache",
			operacion, getName(), key, falla.getClass().getSimpleName(), falla.getMessage());
	}
}