package mx.mexibanco.cuenta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Reto S11 (Redis se cae). Envuelve al RedisCacheWriter de verdad y, si Redis falla o tarda, lo deja en
 * el log y sigue como si la cache estuviera vacia:
 *  - get: regresa null (un miss), y el metodo @Cacheable lee Postgres;
 *  - put: no guarda nada;
 *  - remove (el DEL de @CacheEvict): no borra nada.
 *
 * Por que aqui y no en un CacheErrorHandler: con transactionAware() el DEL de @CacheEvict no corre
 * dentro del aspecto de cache sino en el afterCommit de la transaccion, y ahi el CacheErrorHandler ya
 * no lo atrapa. Si ese DEL lanzara la excepcion, el cargo YA confirmado en Postgres le responderia 500
 * a la saga, y la saga creeria que el cargo no se hizo. Este writer es lo ultimo antes de Redis, asi
 * que atrapa todas las operaciones, vengan de donde vengan.
 *
 * Ademas, cada operacion tiene un LIMITE de tiempo. En la primera version solo se atrapaba la excepcion,
 * pero con Redis apagado cada intento tardaba varios segundos en fallar (el nombre "redis" ya no
 * resuelve y Lettuce tarda en rendirse), y ese tiempo se sumaba a cada cargo y abono: un abono tardo
 * mas de los 2 s que espera la saga, la saga lo dio por fallido y compenso un abono que SI se habia
 * aplicado. La cache nunca debe hacer mas lenta la operacion del dinero.
 *
 * El riesgo que se acepta: un DEL perdido deja una copia vieja hasta que caduque su TTL (30 s). Para
 * MOSTRAR el saldo se acepta; para autorizar un cargo nunca se usa la cache.
 */
public class CacheWriterTolerante implements RedisCacheWriter {

	private static final Logger log = LoggerFactory.getLogger(CacheWriterTolerante.class);

	/** Lo mas que se espera a Redis en cada operacion. */
	private static final long LIMITE_MS = 300;
	/** Despues de una falla, durante este tiempo ni se intenta: se va directo a "sin cache". */
	private static final long SIN_REDIS_MS = 10_000;

	private static final ExecutorService hilos = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "cache-redis");
		t.setDaemon(true);
		return t;
	});

	private final RedisCacheWriter redis;
	private volatile long sinRedisHasta = 0;

	public CacheWriterTolerante(RedisCacheWriter redis) {
		this.redis = redis;
	}

	/** Corre la operacion con limite de tiempo; si falla, tarda o Redis fallo hace poco, regresa siFalla. */
	private <T> T intentar(String operacion, byte[] key, Supplier<T> llamada, T siFalla) {
		if (System.currentTimeMillis() < sinRedisHasta) {
			log.warn("[cache] Redis marcado como caido: {} {} no se intento", operacion, texto(key));
			return siFalla;
		}
		Future<T> futuro = hilos.submit(llamada::get);
		try {
			return futuro.get(LIMITE_MS, TimeUnit.MILLISECONDS);
		} catch (TimeoutException tardo) {
			futuro.cancel(true);
			falla(operacion, key, "no contesto en " + LIMITE_MS + " ms");
		} catch (ExecutionException e) {
			falla(operacion, key, e.getCause().getClass().getSimpleName());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			falla(operacion, key, "interrumpido");
		}
		return siFalla;
	}

	@Override
	public byte[] get(String name, byte[] key) {
		return intentar("GET", key, () -> redis.get(name, key), null);
	}

	@Override
	public byte[] get(String name, byte[] key, Duration ttl) {
		return intentar("GET", key, () -> redis.get(name, key, ttl), null);
	}

	@Override
	public boolean supportsAsyncRetrieve() {
		return false;   // todo pasa por get(), que tiene limite de tiempo
	}

	@Override
	public CompletableFuture<byte[]> retrieve(String name, byte[] key, Duration ttl) {
		return CompletableFuture.completedFuture(get(name, key, ttl));
	}

	@Override
	public void put(String name, byte[] key, byte[] value, Duration ttl) {
		intentar("SET", key, () -> { redis.put(name, key, value, ttl); return null; }, null);
	}

	@Override
	public CompletableFuture<Void> store(String name, byte[] key, byte[] value, Duration ttl) {
		put(name, key, value, ttl);
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public byte[] putIfAbsent(String name, byte[] key, byte[] value, Duration ttl) {
		return intentar("SET NX", key, () -> redis.putIfAbsent(name, key, value, ttl), null);
	}

	@Override
	public void remove(String name, byte[] key) {
		intentar("DEL", key, () -> { redis.remove(name, key); return null; }, null);
	}

	@Override
	public void clean(String name, byte[] pattern) {
		intentar("CLEAN", pattern, () -> { redis.clean(name, pattern); return null; }, null);
	}

	@Override
	public void clearStatistics(String name) {
		redis.clearStatistics(name);
	}

	@Override
	public RedisCacheWriter withStatisticsCollector(CacheStatisticsCollector cacheStatisticsCollector) {
		return new CacheWriterTolerante(redis.withStatisticsCollector(cacheStatisticsCollector));
	}

	@Override
	public CacheStatistics getCacheStatistics(String cacheName) {
		return redis.getCacheStatistics(cacheName);
	}

	private void falla(String operacion, byte[] key, String por) {
		sinRedisHasta = System.currentTimeMillis() + SIN_REDIS_MS;
		log.warn("[cache] Redis no disponible: fallo {} {} ({}); sigo sin cache y no lo intento en {} s",
			operacion, texto(key), por, SIN_REDIS_MS / 1000);
	}

	private static String texto(byte[] key) {
		return key == null ? "?" : new String(key, StandardCharsets.UTF_8);
	}
}
