package mx.mexibanco.cuenta;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v11, reto: el CacheManager de Spring Boot (RedisCacheManager con transactionAware()), pero cada
 * cache envuelta en CacheTolerante.
 *
 * El orden importa. RedisCacheManager entrega TransactionAwareCacheDecorator(RedisCache): el decorador
 * deja el DEL para despues del commit y en ese momento llama a RedisCache.evict(). Si se envolviera
 * por fuera, el DEL de despues del commit seguiria sin proteccion. Por eso aqui se saca la cache real,
 * se envuelve en CacheTolerante y se vuelve a poner el decorador transaccional por fuera:
 *
 *   TransactionAwareCacheDecorator( CacheTolerante( RedisCache ) )
 *
 * Asi el DEL sigue esperando al commit (Parte 1) y, si Redis falla en ese momento, solo queda en el log.
 */
public class CacheManagerTolerante implements CacheManager {

	private final CacheManager real;
	private final Map<String, Cache> envueltas = new ConcurrentHashMap<>();

	public CacheManagerTolerante(CacheManager real) {
		this.real = real;
	}

	@Override
	public Cache getCache(String nombre) {
		return envueltas.computeIfAbsent(nombre, n -> {
			Cache cache = real.getCache(n);
			if (cache == null) {
				return null;
			}
			if (cache instanceof TransactionAwareCacheDecorator transaccional) {
				return new TransactionAwareCacheDecorator(new CacheTolerante(transaccional.getTargetCache()));
			}
			return new CacheTolerante(cache);
		});
	}

	@Override
	public Collection<String> getCacheNames() {
		return real.getCacheNames();
	}
}
