package mx.mexibanco.cuenta;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import java.time.Duration;

// sin @EnableCaching, @Cacheable y @CacheEvict se ignoran sin avisar
@EnableCaching
@Configuration
public class ConfiguracionCache {

    public static final String CUENTAS = "cuentas";

    @Bean
    RedisCacheManagerBuilderCustomizer cacheDeCuentas(
            @Value("${mexibanco.cache.ttl:30s}") Duration ttl, ObjectMapper json) {
        RedisCacheConfiguration cuentas = RedisCacheConfiguration.defaultCacheConfig()
            // 1. cada entrada vive 30 s y luego Redis la borra sola
            .entryTtl(ttl)
            .disableCachingNullValues()
            // 2. el valor se guarda como JSON de CuentaVista, legible con redis-cli
            .serializeValuesWith(SerializationPair.fromSerializer(
                new Jackson2JsonRedisSerializer<>(json, CuentaVista.class)));
		return builder -> builder.withCacheConfiguration(CUENTAS, cuentas)
            // 3. el DEL de @CacheEvict espera al commit de la transacción
            .transactionAware();
        
    }
}