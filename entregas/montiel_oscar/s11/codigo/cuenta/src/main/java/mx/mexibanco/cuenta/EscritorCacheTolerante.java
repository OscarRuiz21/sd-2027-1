package mx.mexibanco.cuenta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheWriter;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * La copia de saldo es prescindible: si Redis falla, un GET se trata como miss
 * y se omite el SET/DEL. Envuelve el escritor real, por debajo del decorador
 * transaccional, para proteger tambien el DEL que corre despues del commit.
 * No oculta errores de Postgres ni de las reglas del negocio.
 */
public class EscritorCacheTolerante implements RedisCacheWriter {
    private static final Logger log = LoggerFactory.getLogger(EscritorCacheTolerante.class);
    private final RedisCacheWriter delegado;

    public EscritorCacheTolerante(RedisCacheWriter delegado) {
        this.delegado = delegado;
    }

    private <T> T intentar(String operacion, Supplier<T> accion) {
        try {
            return accion.get();
        } catch (DataAccessException fallo) {
            registrar(operacion, fallo);
            return null;
        }
    }

    private static void registrar(String operacion, Throwable fallo) {
        log.warn("[cache] fallo {}: {}; se continua con Postgres", operacion, fallo.getClass().getSimpleName());
    }

    @Override
    public byte[] get(String nombre, byte[] clave) {
        return intentar("GET", () -> delegado.get(nombre, clave));
    }

    @Override
    public byte[] get(String nombre, byte[] clave, Duration ttl) {
        return intentar("GET", () -> delegado.get(nombre, clave, ttl));
    }

    @Override
    public void put(String nombre, byte[] clave, byte[] valor, Duration ttl) {
        intentar("SET", () -> { delegado.put(nombre, clave, valor, ttl); return null; });
    }

    @Override
    public byte[] putIfAbsent(String nombre, byte[] clave, byte[] valor, Duration ttl) {
        return intentar("SETNX", () -> delegado.putIfAbsent(nombre, clave, valor, ttl));
    }

    @Override
    public void remove(String nombre, byte[] clave) {
        intentar("DEL", () -> { delegado.remove(nombre, clave); return null; });
    }

    @Override
    public void clean(String nombre, byte[] patron) {
        intentar("CLEAN", () -> { delegado.clean(nombre, patron); return null; });
    }

    private <T> CompletableFuture<T> asincrono(String operacion, Supplier<CompletableFuture<T>> accion) {
        try {
            return accion.get().exceptionally(error -> {
                Throwable causa = error instanceof CompletionException ? error.getCause() : error;
                if (causa instanceof DataAccessException) {
                    registrar(operacion, causa);
                    return null;
                }
                throw new CompletionException(causa);
            });
        } catch (DataAccessException error) {
            registrar(operacion, error);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public boolean supportsAsyncRetrieve() { return delegado.supportsAsyncRetrieve(); }

    @Override
    public CompletableFuture<byte[]> retrieve(String nombre, byte[] clave, Duration ttl) {
        return asincrono("GET async", () -> delegado.retrieve(nombre, clave, ttl));
    }

    @Override
    public CompletableFuture<Void> store(String nombre, byte[] clave, byte[] valor, Duration ttl) {
        return asincrono("SET async", () -> delegado.store(nombre, clave, valor, ttl));
    }

    @Override
    public CacheStatistics getCacheStatistics(String nombre) { return delegado.getCacheStatistics(nombre); }

    @Override
    public void clearStatistics(String nombre) { delegado.clearStatistics(nombre); }

    @Override
    public RedisCacheWriter withStatisticsCollector(CacheStatisticsCollector colector) {
        return new EscritorCacheTolerante(delegado.withStatisticsCollector(colector));
    }
}
