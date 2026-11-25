package mx.mexibanco.cuenta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;

class CacheDeSaldo extends RedisCache {

	private static final Logger LOG = LoggerFactory.getLogger(CacheDeSaldo.class);

	CacheDeSaldo(String nombre, RedisCacheWriter escritor, RedisCacheConfiguration configuracion) {
		super(nombre, escritor, configuracion);
	}

	@Override
	protected Object lookup(Object clave) {
		try {
			return super.lookup(clave);
		} catch (DataAccessException error) {
			LOG.warn("Redis GET falló para {}: {}", clave, error.getMessage());
			return null;
		}
	}

	@Override
	public void put(Object clave, Object valor) {
		try {
			super.put(clave, valor);
		} catch (DataAccessException error) {
			LOG.warn("Redis SET falló para {}: {}", clave, error.getMessage());
		}
	}

	@Override
	public void evict(Object clave) {
		try {
			super.evict(clave);
		} catch (DataAccessException error) {
			LOG.warn("Redis DEL falló para {}: {}", clave, error.getMessage());
		}
	}
}
