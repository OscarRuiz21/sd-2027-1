package mx.mexibanco.cuenta;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * v11, reto: con Redis caido, la cache no lanza excepciones; se comporta como si estuviera vacia.
 * "Redis caido" se simula con una cache que falla en cada operacion.
 */
class CacheToleranteTest {

	/** Una cache que falla siempre, como RedisCache cuando Redis no contesta. */
	static class CacheCaida extends ConcurrentMapCache {
		CacheCaida() {
			super(ConfiguracionCache.CUENTAS);
		}

		@Override
		public ValueWrapper get(Object key) {
			throw new RedisConnectionFailureException("Redis no contesta");
		}

		@Override
		public void put(Object key, Object value) {
			throw new RedisConnectionFailureException("Redis no contesta");
		}

		@Override
		public void evict(Object key) {
			throw new RedisConnectionFailureException("Redis no contesta");
		}
	}

	private final CuentaVista ana = new CuentaVista("002180000000000001", "Ana", new BigDecimal("1000"), Instant.now());

	@Test
	void conRedisCaidoUnGetEsUnMissYNoUnError() {
		Cache cache = new CacheTolerante(new CacheCaida());

		assertThat(cache.get("002180000000000001")).isNull();
	}

	@Test
	void conRedisCaidoElPutYElDelNoLanzanNada() {
		Cache cache = new CacheTolerante(new CacheCaida());

		assertThatCode(() -> cache.put("002180000000000001", ana)).doesNotThrowAnyException();
		assertThatCode(() -> cache.evict("002180000000000001")).doesNotThrowAnyException();
	}

	@Test
	void conRedisArribaFuncionaIgualQueLaCacheReal() {
		Cache cache = new CacheTolerante(new ConcurrentMapCache(ConfiguracionCache.CUENTAS));

		cache.put("002180000000000001", ana);
		assertThat(cache.get("002180000000000001").get()).isEqualTo(ana);
		cache.evict("002180000000000001");
		assertThat(cache.get("002180000000000001")).isNull();
	}

	/** El decorador transaccional queda POR FUERA: el DEL sigue esperando al commit, y protegido. */
	@Test
	void elManagerToleranteConservaTransactionAwareYEnvuelveLaCacheReal() {
		SimpleCacheManager real = new SimpleCacheManager();
		real.setCaches(List.of(new CacheCaida()));
		real.afterPropertiesSet();
		CacheManager transaccional = new CacheManager() {
			@Override
			public Cache getCache(String nombre) {
				Cache c = real.getCache(nombre);
				return c == null ? null : new TransactionAwareCacheDecorator(c);
			}

			@Override
			public java.util.Collection<String> getCacheNames() {
				return real.getCacheNames();
			}
		};

		Cache cache = new CacheManagerTolerante(transaccional).getCache(ConfiguracionCache.CUENTAS);

		assertThat(cache).isInstanceOf(TransactionAwareCacheDecorator.class);
		assertThat(((TransactionAwareCacheDecorator) cache).getTargetCache()).isInstanceOf(CacheTolerante.class);
		// Sin transaccion activa, el decorador hace el DEL de inmediato: no debe lanzar nada.
		assertThatCode(() -> cache.evict("002180000000000001")).doesNotThrowAnyException();
	}
}
