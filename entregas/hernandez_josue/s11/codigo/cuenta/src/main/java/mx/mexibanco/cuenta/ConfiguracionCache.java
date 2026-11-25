package mx.mexibanco.cuenta;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import java.util.Map;

@Configuration
@EnableCaching
public class ConfiguracionCache {

	public static final String CUENTAS = "cuentas";

	@Bean
	@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
	RedisCacheManager cacheDeCuentas(RedisConnectionFactory conexion, ObjectMapper json,
								   @Value("${mexibanco.cache.ttl:30s}") Duration ttl) {
		var configuracion = RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(ttl)
			.disableCachingNullValues()
			.serializeValuesWith(SerializationPair.fromSerializer(
				new Jackson2JsonRedisSerializer<>(json, CuentaVista.class)));

		var escritor = RedisCacheWriter.nonLockingRedisCacheWriter(conexion);
		var gestor = new RedisCacheManager(escritor, configuracion, Map.of(CUENTAS, configuracion)) {
			@Override
			protected RedisCache createRedisCache(String nombre, RedisCacheConfiguration ajustes) {
				return new CacheDeSaldo(nombre, escritor, ajustes);
			}
		};
		// El decorador de Spring programa el DEL después del commit. CacheDeSaldo maneja el fallo ahí.
		gestor.setTransactionAware(true);
		return gestor;
	}
}
