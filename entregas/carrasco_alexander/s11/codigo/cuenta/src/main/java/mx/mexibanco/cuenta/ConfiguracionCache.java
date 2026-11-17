package mx.mexibanco.cuenta;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@EnableCaching
@Configuration
public class ConfiguracionCache {

	/** El nombre de la cache. En Redis cada entrada queda como la clave "cuentas::<clabe>". */
	public static final String CUENTAS = "cuentas";

	// Con CACHE_TIPO=none (spring.cache.type=none) este bean no se crea y Spring Boot pone su cache apagada.
	@Bean
	@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
	CacheManager cacheManager(RedisConnectionFactory conexion,
			@Value("${mexibanco.cache.ttl:30s}") Duration ttl, ObjectMapper json) {
		RedisCacheConfiguration cuentas = RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(ttl)
			.disableCachingNullValues()
			.serializeValuesWith(SerializationPair.fromSerializer(
				new Jackson2JsonRedisSerializer<>(json, CuentaVista.class)));

		RedisCacheManager manager = new RedisCacheManager(
				RedisCacheWriter.nonLockingRedisCacheWriter(conexion),
				RedisCacheConfiguration.defaultCacheConfig(),
				Map.of(CUENTAS, cuentas)) {
			@Override
			protected Cache decorateCache(Cache cache) {
				// primero la capa tolerante, luego (por super) transactionAware
				return super.decorateCache(new CacheTolerante(cache));
			}
		};
		manager.setTransactionAware(true);   // put y evict esperan al commit
		return manager;
	}
}