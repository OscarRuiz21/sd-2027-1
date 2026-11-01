package mx.mexibanco.cuenta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * S11 (reto): el RedisCacheWriter que usa la cache "cuentas", tolerante a que Redis se caiga.
 *
 * Por que aqui y no en un CacheErrorHandler: con transactionAware(), @CacheEvict solo REGISTRA el
 * borrado y el DEL real corre despues del commit (afterCommit). El CacheErrorHandler envuelve el
 * registro, no el DEL. Si Redis esta caido, la excepcion del DEL sale de cargar()/abonar() cuando el
 * dinero YA se movio en Postgres: cuenta contestaria 500 a un cargo confirmado y la saga creeria que
 * fallo. El writer es la pieza que de verdad habla con Redis, asi que atrapar aqui cubre el GET, el
 * SET y tambien el DEL de despues del commit.
 *
 * Reglas: la cache es una copia para MOSTRAR, asi que ninguna falla de Redis tumba nada.
 *  - get que falla = miss: el GET va a Postgres (mas lento, pero contesta 200);
 *  - put o remove que falla = se registra en el log y se sigue.
 *
 * Tiempo maximo: cada operacion corre en un hilo aparte y se espera a lo mas LIMITE_MS. Los timeouts del
 * cliente no bastan: con el contenedor de Redis detenido, resolver el nombre "redis" tarda ~3 s (el DNS
 * de Docker le pregunta al del host) y eso no lo cubre ni timeout ni connect-timeout. Con un limite
 * propio, una operacion de cache nunca se acerca al timeout de lectura de la saga (2 s).
 *
 * Interruptor simple: despues de una falla, durante PAUSA_MS no se intenta Redis. El riesgo aceptado:
 * un DEL que no se hizo deja una copia vieja a lo mas por el TTL (30 s), el mismo limite que vimos en
 * la Parte 4.
 */
class EscritorDeCacheTolerante implements RedisCacheWriter {

	private static final Logger log = LoggerFactory.getLogger(EscritorDeCacheTolerante.class);
	private static final long PAUSA_MS = 5_000;
	private static final long LIMITE_MS = 300;

	/**
	 * Hilos para hablar con Redis: a lo mas 4 y sin cola. Si estan todos ocupados (Redis colgado), la
	 * operacion se rechaza al instante y cuenta como falla, en vez de acumular espera.
	 */
	private static final ExecutorService HILOS = new ThreadPoolExecutor(0, 4, 30, TimeUnit.SECONDS,
		new SynchronousQueue<>(), r -> {
			Thread hilo = new Thread(r, "cache-redis");
			hilo.setDaemon(true);
			return hilo;
		});

	private final RedisCacheWriter redis;
	private final AtomicLong sinRedisHasta = new AtomicLong();

	EscritorDeCacheTolerante(RedisCacheWriter redis) {
		this.redis = redis;
	}

	private boolean enPausa() {
		return System.currentTimeMillis() < sinRedisHasta.get();
	}

	private void fallo(String operacion, String cache, byte[] clave, Throwable e) {
		sinRedisHasta.set(System.currentTimeMillis() + PAUSA_MS);
		log.warn("[cache] fallo {} de {} en Redis ({}); sigo sin cache {} ms",
			operacion, clave == null ? cache : new String(clave), e.getClass().getSimpleName(), PAUSA_MS);
	}

	private <T> T intentar(String operacion, String cache, byte[] clave, Supplier<T> accion, T siFalla) {
		if (enPausa()) {
			log.debug("[cache] Redis en pausa: se omite {} de {}", operacion, cache);
			return siFalla;
		}
		Future<T> pendiente = null;
		try {
			pendiente = HILOS.submit((java.util.concurrent.Callable<T>) accion::get);
			T resultado = pendiente.get(LIMITE_MS, TimeUnit.MILLISECONDS);
			if (sinRedisHasta.getAndSet(0) != 0) {
				log.info("[cache] Redis contesta otra vez");
			}
			return resultado;
		} catch (TimeoutException | RejectedExecutionException e) {
			if (pendiente != null) {
				pendiente.cancel(true);
			}
			fallo(operacion, cache, clave, e);
			return siFalla;
		} catch (java.util.concurrent.ExecutionException e) {
			fallo(operacion, cache, clave, e.getCause() == null ? e : e.getCause());
			return siFalla;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			fallo(operacion, cache, clave, e);
			return siFalla;
		}
	}

	@Override
	public byte[] get(String name, byte[] key) {
		return intentar("GET", name, key, () -> redis.get(name, key), null);
	}

	@Override
	public byte[] get(String name, byte[] key, Duration ttl) {
		return intentar("GET", name, key, () -> redis.get(name, key, ttl), null);
	}

	@Override
	public void put(String name, byte[] key, byte[] value, Duration ttl) {
		intentar("SET", name, key, () -> { redis.put(name, key, value, ttl); return null; }, null);
	}

	@Override
	public byte[] putIfAbsent(String name, byte[] key, byte[] value, Duration ttl) {
		return intentar("SET NX", name, key, () -> redis.putIfAbsent(name, key, value, ttl), null);
	}

	@Override
	public void remove(String name, byte[] key) {
		intentar("DEL", name, key, () -> { redis.remove(name, key); return null; }, null);
	}

	@Override
	public void clean(String name, byte[] pattern) {
		intentar("CLEAN", name, pattern, () -> { redis.clean(name, pattern); return null; }, null);
	}

	// Lo asincrono (CompletableFuture) no lo usa este servicio; se delega y una falla se vuelve miss.
	@Override
	public boolean supportsAsyncRetrieve() {
		return redis.supportsAsyncRetrieve();
	}

	@Override
	public CompletableFuture<byte[]> retrieve(String name, byte[] key, Duration ttl) {
		if (enPausa()) {
			return CompletableFuture.completedFuture(null);
		}
		return redis.retrieve(name, key, ttl).exceptionally(e -> {
			fallo("GET async", name, key, new RuntimeException(e));
			return null;
		});
	}

	@Override
	public CompletableFuture<Void> store(String name, byte[] key, byte[] value, Duration ttl) {
		if (enPausa()) {
			return CompletableFuture.completedFuture(null);
		}
		return redis.store(name, key, value, ttl).exceptionally(e -> {
			fallo("SET async", name, key, new RuntimeException(e));
			return null;
		});
	}

	@Override
	public void clearStatistics(String name) {
		redis.clearStatistics(name);
	}

	@Override
	public RedisCacheWriter withStatisticsCollector(CacheStatisticsCollector cacheStatisticsCollector) {
		return new EscritorDeCacheTolerante(redis.withStatisticsCollector(cacheStatisticsCollector));
	}

	@Override
	public CacheStatistics getCacheStatistics(String cacheName) {
		return redis.getCacheStatistics(cacheName);
	}
}
