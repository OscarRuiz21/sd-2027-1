package mx.mexibanco.cuenta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Lab S11, reto: que Redis caido no tumbe a cuenta. Envuelve al RedisCacheWriter de Spring (la pieza
 * que de verdad habla con Redis) y, si Redis no contesta, deja el error en el log y sigue como si la
 * cache estuviera vacia: un GET es un miss (se lee Postgres), un SET o un DEL no hacen nada.
 *
 * Por que aqui y no en un CacheErrorHandler: con transactionAware(), @CacheEvict no borra en el
 * momento, solo registra el DEL para despues del commit. El CacheErrorHandler envuelve esa llamada
 * (que no falla) pero no el DEL real, que corre en afterCommit, fuera de su alcance. Si ese DEL
 * truena, el cargo YA se confirmo en Postgres pero cuenta contesta 500, y la saga creeria que el cargo
 * no ocurrio. Este writer esta debajo de todo eso, asi que atrapa tambien el DEL de despues del commit.
 *
 * Dos cosas mas, por lo que vi al probarlo: con el contenedor de Redis apagado, cada intento de
 * reconexion resuelve el nombre "redis" por DNS y eso tarda unos 5 s, mas que el timeout de 2 s con el que
 * la saga llama a cuenta. Por eso:
 *  - cada operacion con Redis tiene un tope (LIMITE, el mismo timeout de spring.data.redis); si no
 *    contesta en ese tiempo, cuenta como falla;
 *  - despues de una falla, durante PAUSA no se intenta Redis y se sigue directo sin cache, para que solo
 *    la primera operacion pague la espera. Pasada la pausa se vuelve a intentar sola.
 *
 * El riesgo que se acepta: si el DEL falla (o se salta durante la pausa), una copia vieja puede quedarse
 * en Redis hasta su TTL (30 s), igual que en la Parte 4. Se muestra un saldo viejo, pero nunca se decide
 * con el. (Si Redis se reinicia arranca vacio, porque no tiene persistencia.)
 */
class CacheWriterTolerante implements RedisCacheWriter {

	private static final Logger log = LoggerFactory.getLogger(CacheWriterTolerante.class);

	/** Lo mas que se espera a Redis en cada operacion. */
	static final Duration LIMITE = Duration.ofMillis(500);
	/** Cuanto tiempo se deja de intentar Redis despues de una falla. */
	static final Duration PAUSA = Duration.ofSeconds(10);

	/** Hilos aparte para poder dejar de esperar a Redis sin cortar el hilo de la peticion. */
	private static final ExecutorService HILOS = Executors.newCachedThreadPool(tarea -> {
		Thread hilo = new Thread(tarea, "cache-redis");
		hilo.setDaemon(true);
		return hilo;
	});

	private final RedisCacheWriter redis;
	/** Hasta cuando (System.nanoTime) no se intenta Redis. Compartido por todas las copias del writer. */
	private final AtomicLong sinRedisHasta;

	CacheWriterTolerante(RedisCacheWriter redis) {
		this(redis, new AtomicLong(System.nanoTime()));
	}

	private CacheWriterTolerante(RedisCacheWriter redis, AtomicLong sinRedisHasta) {
		this.redis = redis;
		this.sinRedisHasta = sinRedisHasta;
	}

	@Override
	public byte[] get(String nombre, byte[] clave) {
		return intentar("GET", nombre, clave, () -> redis.get(nombre, clave), null);
	}

	@Override
	public byte[] get(String nombre, byte[] clave, Duration ttl) {
		return intentar("GET", nombre, clave, () -> redis.get(nombre, clave, ttl), null);
	}

	@Override
	public boolean supportsAsyncRetrieve() {
		return redis.supportsAsyncRetrieve();
	}

	@Override
	public CompletableFuture<byte[]> retrieve(String nombre, byte[] clave, Duration ttl) {
		return redis.retrieve(nombre, clave, ttl).exceptionally(error -> {
			avisar("GET", nombre, clave, error);
			return null;
		});
	}

	@Override
	public void put(String nombre, byte[] clave, byte[] valor, Duration ttl) {
		intentar("SET", nombre, clave, () -> {
			redis.put(nombre, clave, valor, ttl);
			return null;
		}, null);
	}

	@Override
	public CompletableFuture<Void> store(String nombre, byte[] clave, byte[] valor, Duration ttl) {
		return redis.store(nombre, clave, valor, ttl).exceptionally(error -> {
			avisar("SET", nombre, clave, error);
			return null;
		});
	}

	@Override
	public byte[] putIfAbsent(String nombre, byte[] clave, byte[] valor, Duration ttl) {
		return intentar("SET NX", nombre, clave, () -> redis.putIfAbsent(nombre, clave, valor, ttl), null);
	}

	@Override
	public void remove(String nombre, byte[] clave) {
		intentar("DEL", nombre, clave, () -> {
			redis.remove(nombre, clave);
			return null;
		}, null);
	}

	@Override
	public void clean(String nombre, byte[] patron) {
		intentar("CLEAN", nombre, patron, () -> {
			redis.clean(nombre, patron);
			return null;
		}, null);
	}

	@Override
	public void clearStatistics(String nombre) {
		redis.clearStatistics(nombre);
	}

	@Override
	public RedisCacheWriter withStatisticsCollector(CacheStatisticsCollector estadisticas) {
		return new CacheWriterTolerante(redis.withStatisticsCollector(estadisticas), sinRedisHasta);
	}

	@Override
	public CacheStatistics getCacheStatistics(String nombre) {
		return redis.getCacheStatistics(nombre);
	}

	/**
	 * Corre la operacion con un tope de LIMITE. Si Redis falla o tarda de mas, lo dice en el log, abre la
	 * PAUSA y regresa el valor de "cache vacia". Durante la pausa ni siquiera lo intenta.
	 */
	private <T> T intentar(String operacion, String nombre, byte[] clave, Supplier<T> accion, T siFalla) {
		if (System.nanoTime() - sinRedisHasta.get() < 0) {
			log.debug("[cache] en pausa, sin Redis: me salto el {} de {}", operacion, new String(clave, StandardCharsets.UTF_8));
			return siFalla;
		}
		try {
			return CompletableFuture.supplyAsync(accion, HILOS).get(LIMITE.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException tardoDeMas) {
			fallo(operacion, nombre, clave, new IllegalStateException("no contesto en " + LIMITE.toMillis() + " ms"));
		} catch (ExecutionException error) {
			if (!(error.getCause() instanceof DataAccessException)) {
				throw new IllegalStateException(error.getCause());
			}
			fallo(operacion, nombre, clave, error.getCause());
		} catch (InterruptedException interrumpido) {
			Thread.currentThread().interrupt();
		}
		return siFalla;
	}

	private void fallo(String operacion, String nombre, byte[] clave, Throwable error) {
		sinRedisHasta.set(System.nanoTime() + PAUSA.toNanos());
		avisar(operacion, nombre, clave, error);
	}

	private static void avisar(String operacion, String nombre, byte[] clave, Throwable error) {
		log.warn("[cache] Redis no contesto el {} de {} ({}); sigo sin cache {} s: {}", operacion,
			new String(clave, StandardCharsets.UTF_8), nombre, PAUSA.toSeconds(), error.getMessage());
	}
}
