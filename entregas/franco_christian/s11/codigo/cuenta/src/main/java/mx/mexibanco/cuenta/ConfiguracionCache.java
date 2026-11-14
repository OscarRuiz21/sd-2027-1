package mx.mexibanco.cuenta;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * v11: la cache de GET /cuentas/{clabe} en Redis, con el patron cache-aside.
 *
 * Reto: Resiliencia ante caida de Redis. Si Redis se cae, la cache no debe tirar ni los GET ni
 * las transferencias (cargos/abonos). Se envuelve en ResilientCache para que las lecturas degraden a
 * Postgres y los DEL en afterCommit() no lancen excepcion que arruine la saga.
 */
@EnableCaching
@Configuration
public class ConfiguracionCache {

	private static final Logger log = LoggerFactory.getLogger(ConfiguracionCache.class);

	/** El nombre de la cache. En Redis cada entrada queda como la clave "cuentas::<clabe>". */
	public static final String CUENTAS = "cuentas";

	@Bean
	@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
	CacheManager cacheManager(RedisConnectionFactory connectionFactory,
			@Value("${mexibanco.cache.ttl:30s}") Duration ttl,
			ObjectMapper json) {
		RedisCacheConfiguration cuentas = RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(ttl)
			.disableCachingNullValues()
			.serializeValuesWith(SerializationPair.fromSerializer(
				new Jackson2JsonRedisSerializer<>(json, CuentaVista.class)));

		RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);
		RedisCacheManager targetManager = RedisCacheManager.builder(writer)
			.cacheDefaults(cuentas)
			.withCacheConfiguration(CUENTAS, cuentas)
			.build();

		return new ResilientCacheManager(targetManager);
	}

	/**
	 * Administrador de cache que envuelve cada cache en ResilientCache y TransactionAwareCacheDecorator.
	 */
	static class ResilientCacheManager implements CacheManager {
		private final RedisCacheManager delegate;

		ResilientCacheManager(RedisCacheManager delegate) {
			this.delegate = delegate;
		}

		@Override
		public Cache getCache(String name) {
			Cache cache = delegate.getCache(name);
			if (cache == null) {
				return null;
			}
			return new TransactionAwareCacheDecorator(new ResilientCache(cache));
		}

		@Override
		public Collection<String> getCacheNames() {
			return delegate.getCacheNames();
		}
	}

	/**
	 * Decorador resiliente que atrapa cualquier error de conexion o comando contra Redis, registrando
	 * la operacion que fallo en el log sin propagar excepciones que tiren el servicio.
	 */
	static class ResilientCache implements Cache {
		private final Cache delegate;

		ResilientCache(Cache delegate) {
			this.delegate = delegate;
		}

		@Override
		public String getName() {
			return delegate.getName();
		}

		@Override
		public Object getNativeCache() {
			return delegate.getNativeCache();
		}

		@Override
		public ValueWrapper get(Object key) {
			try {
				return delegate.get(key);
			} catch (RuntimeException e) {
				log.warn("[cache] fallo al leer clave {} de Redis: {}", key, e.getMessage());
				return null;
			}
		}

		@Override
		public <T> T get(Object key, Class<T> type) {
			try {
				return delegate.get(key, type);
			} catch (RuntimeException e) {
				log.warn("[cache] fallo al leer clave {} con tipo de Redis: {}", key, e.getMessage());
				return null;
			}
		}

		@Override
		public <T> T get(Object key, Callable<T> valueLoader) {
			try {
				return delegate.get(key, valueLoader);
			} catch (RuntimeException e) {
				log.warn("[cache] fallo al cargar clave {} de Redis: {}", key, e.getMessage());
				try {
					return valueLoader.call();
				} catch (Exception ex) {
					throw new ValueRetrievalException(key, valueLoader, ex);
				}
			}
		}

		@Override
		public void put(Object key, Object value) {
			try {
				delegate.put(key, value);
			} catch (RuntimeException e) {
				log.warn("[cache] fallo al guardar clave {} en Redis: {}", key, e.getMessage());
			}
		}

		@Override
		public ValueWrapper putIfAbsent(Object key, Object value) {
			try {
				return delegate.putIfAbsent(key, value);
			} catch (RuntimeException e) {
				log.warn("[cache] fallo al putIfAbsent clave {} en Redis: {}", key, e.getMessage());
				return null;
			}
		}

		@Override
		public void evict(Object key) {
			try {
				delegate.evict(key);
			} catch (RuntimeException e) {
				log.warn("[cache] fallo al borrar (DEL) clave {} de Redis: {}", key, e.getMessage());
			}
		}

		@Override
		public boolean evictIfPresent(Object key) {
			try {
				return delegate.evictIfPresent(key);
			} catch (RuntimeException e) {
				log.warn("[cache] fallo al evictIfPresent clave {} de Redis: {}", key, e.getMessage());
				return false;
			}
		}

		@Override
		public void clear() {
			try {
				delegate.clear();
			} catch (RuntimeException e) {
				log.warn("[cache] fallo al limpiar cache en Redis: {}", e.getMessage());
			}
		}

		@Override
		public boolean invalidate() {
			try {
				return delegate.invalidate();
			} catch (RuntimeException e) {
				log.warn("[cache] fallo al invalidar cache en Redis: {}", e.getMessage());
				return false;
			}
		}
	}
}
