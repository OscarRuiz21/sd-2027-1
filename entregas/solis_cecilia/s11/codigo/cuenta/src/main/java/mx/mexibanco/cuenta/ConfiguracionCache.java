package mx.mexibanco.cuenta;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

@EnableCaching
@Configuration
public class ConfiguracionCache {
    public static final String CUENTAS = "cuentas";
    private static final Logger log = LoggerFactory.getLogger(ConfiguracionCache.class);

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    RedisCacheManager cacheManager(RedisConnectionFactory conexion,
            @Value("${mexibanco.cache.ttl:30s}") Duration ttl, ObjectMapper json) {
        RedisCacheConfiguration configuracion = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(ttl)
            .disableCachingNullValues()
            .serializeValuesWith(SerializationPair.fromSerializer(
                new Jackson2JsonRedisSerializer<>(json, CuentaVista.class)));
        RedisCacheWriter escritor = RedisCacheWriter.nonLockingRedisCacheWriter(conexion);
        RedisCacheManager manager = new RedisCacheManager(escritor, configuracion) {
            @Override
            protected RedisCache createRedisCache(String nombre, RedisCacheConfiguration config) {
                return new CacheTolerante(nombre, escritor, config != null ? config : configuracion);
            }
        };

        // El decorador transaccional envuelve a CacheTolerante. Por eso el DEL
        // diferido tambien queda protegido cuando se ejecuta despues del commit.
        manager.setTransactionAware(true);
        return manager;
    }

    static class CacheTolerante extends RedisCache {
        CacheTolerante(String nombre, RedisCacheWriter escritor, RedisCacheConfiguration config) {
            super(nombre, escritor, config);
        }

        private void fallo(String operacion, Object clave, RuntimeException error) {
            log.warn("[cache] fallo {} en {} clave={}: {}; se continua sin cache",
                operacion, getName(), clave, error.toString());
        }

        @Override
        protected Object lookup(Object clave) {
            try {
                return super.lookup(clave);
            } catch (RuntimeException error) {
                fallo("GET", clave, error);
                return null; // Miss: @Cacheable ejecuta la consulta de PostgreSQL.
            }
        }

        @Override
        public void put(Object clave, Object valor) {
            try {
                super.put(clave, valor);
            } catch (RuntimeException error) {
                fallo("SET", clave, error);
            }
        }

        @Override
        public void evict(Object clave) {
            try {
                super.evict(clave);
            } catch (RuntimeException error) {
                fallo("DEL", clave, error);
            }
        }

        @Override
        public Cache.ValueWrapper putIfAbsent(Object clave, Object valor) {
            try {
                return super.putIfAbsent(clave, valor);
            } catch (RuntimeException error) {
                fallo("SETNX", clave, error);
                return null;
            }
        }

        @Override
        public void clear() {
            try {
                super.clear();
            } catch (RuntimeException error) {
                fallo("CLEAR", "*", error);
            }
        }
    }
}