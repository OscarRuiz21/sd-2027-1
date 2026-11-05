package mx.mexibanco.cuenta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.lang.Nullable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class ToleranteRedisCacheWriter implements RedisCacheWriter {
    private static final Logger log = LoggerFactory.getLogger(ToleranteRedisCacheWriter.class);
    private final RedisCacheWriter delegate;

    public ToleranteRedisCacheWriter(RedisCacheWriter delegate) {
        this.delegate = delegate;
    }

    @Override
    public void put(String name, byte[] key, byte[] value, @Nullable Duration ttl) {
        try {
            delegate.put(name, key, value, ttl);
        } catch (Exception e) {
            log.warn("Fallo al escribir en Redis (Caché {}): {}", name, e.getMessage());
        }
    }

    @Override
    public byte[] get(String name, byte[] key) {
        try {
            return delegate.get(name, key);
        } catch (Exception e) {
            log.warn("Fallo al leer de Redis (Caché {}): {}", name, e.getMessage());
            // Si Redis está caído, devolvemos null para forzar un 'cache miss' hacia Postgres
            return null; 
        }
    }

    @Override
    public byte[] putIfAbsent(String name, byte[] key, byte[] value, @Nullable Duration ttl) {
        try {
            return delegate.putIfAbsent(name, key, value, ttl);
        } catch (Exception e) {
            log.warn("Fallo al ejecutar putIfAbsent en Redis (Caché {}): {}", name, e.getMessage());
            return null;
        }
    }

    @Override
    public void remove(String name, byte[] key) {
        try {
            delegate.remove(name, key);
        } catch (Exception e) {
            log.warn("Fallo al eliminar llave en Redis tras commit (Caché {}): {}", name, e.getMessage());
        }
    }

    @Override
    public void clean(String name, byte[] pattern) {
        try {
            delegate.clean(name, pattern);
        } catch (Exception e) {
            log.warn("Fallo al limpiar caché en Redis (Caché {}): {}", name, e.getMessage());
        }
    }

    @Override
    public void clearStatistics(String name) {
        delegate.clearStatistics(name);
    }

    @Override
    public RedisCacheWriter withStatisticsCollector(CacheStatisticsCollector cacheStatisticsCollector) {
        return delegate.withStatisticsCollector(cacheStatisticsCollector);
    }

    @Override
    public CacheStatistics getCacheStatistics(String cacheName) {
        return delegate.getCacheStatistics(cacheName);
    }

   @Override
    public CompletableFuture<Void> store(String name, byte[] key, byte[] value, @Nullable Duration ttl) {
        try {
            return delegate.store(name, key, value, ttl);
        } catch (Exception e) {
            log.warn("Fallo al guardar (store) en Redis (Caché {}): {}", name, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<byte[]> retrieve(String name, byte[] key, @Nullable Duration ttl) {
        try {
            return delegate.retrieve(name, key, ttl);
        } catch (Exception e) {
            log.warn("Fallo al leer (retrieve) de Redis (Caché {}): {}", name, e.getMessage());
            return CompletableFuture.completedFuture(null); // Forzamos un cache miss hacia Postgres si Redis cae
        }
    }
}
