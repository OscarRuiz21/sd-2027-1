package mx.mexibanco.cuenta;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@EnableCaching
@Configuration
public class ConfiguracionCache {
    public static final String CUENTAS = "cuentas";
    @Bean
    RedisCacheManagerBuilderCustomizer cacheDeCuentas(
            @Value("${mexibanco.cache.ttl:30s}") Duration ttl, ObjectMapper json) {
        var cuentas = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(ttl).disableCachingNullValues()
            .serializeValuesWith(SerializationPair.fromSerializer(
                new Jackson2JsonRedisSerializer<>(json, CuentaVista.class)));
        return builder -> builder.withCacheConfiguration(CUENTAS, cuentas).transactionAware();
    }
}
