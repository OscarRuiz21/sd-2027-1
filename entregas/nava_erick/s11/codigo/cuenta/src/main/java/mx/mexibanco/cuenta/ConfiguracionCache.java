
package mx.mexibanco.cuenta;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

@Configuration
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
public class ConfiguracionCache implements CachingConfigurer {

    private static final Logger log =
            LoggerFactory.getLogger(ConfiguracionCache.class);

    public static final String CUENTAS = "cuentas";

    @Bean
    RedisCacheManagerBuilderCustomizer cacheDeCuentas(
            @Value("${mexibanco.cache.ttl:30s}") Duration ttl,
            ObjectMapper json) {

        RedisCacheConfiguration cuentas =
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(ttl)
                    .disableCachingNullValues()
                    .serializeValuesWith(
                        SerializationPair.fromSerializer(
                            new Jackson2JsonRedisSerializer<>(
                                json, CuentaVista.class
                            )
                        )
                    );

        return builder -> builder
                .withCacheConfiguration(CUENTAS, cuentas)
                .transactionAware();
    }

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {

        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(
                    RuntimeException ex,
                    Cache cache,
                    Object key) {

                registrarError("GET", cache, key, ex);
            }

            @Override
            public void handleCachePutError(
                    RuntimeException ex,
                    Cache cache,
                    Object key,
                    Object value) {

                registrarError("SET", cache, key, ex);
            }

            @Override
            public void handleCacheEvictError(
                    RuntimeException ex,
                    Cache cache,
                    Object key) {

                registrarError("DEL", cache, key, ex);
            }

            @Override
            public void handleCacheClearError(
                    RuntimeException ex,
                    Cache cache) {

                registrarError("CLEAR", cache, "-", ex);
            }

            private void registrarError(
                    String operacion,
                    Cache cache,
                    Object key,
                    RuntimeException ex) {

                log.warn(
                    "[cache] Fallo de {} en {} clave={}: {}",
                    operacion,
                    cache.getName(),
                    key,
                    ex.toString()
                );
            }
        };
    }
}
