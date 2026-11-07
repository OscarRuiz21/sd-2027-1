package mx.mexibanco.cuenta;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

/**
 * v11: la cache de GET /cuentas/{clabe} en Redis (Parte 1) y, en el reto, que Redis caido no tumbe a cuenta.
 */
@EnableCaching   // sin esto, @Cacheable y @CacheEvict se ignoran sin avisar
@Configuration
public class ConfiguracionCache {

	/** El nombre de la cache. En Redis cada entrada queda como la clave "cuentas::<clabe>". */
	public static final String CUENTAS = "cuentas";

	@Bean
	RedisCacheManagerBuilderCustomizer cacheDeCuentas(
			@Value("${mexibanco.cache.ttl:30s}") Duration ttl, ObjectMapper json,
			RedisConnectionFactory conexiones) {
		RedisCacheConfiguration cuentas = RedisCacheConfiguration.defaultCacheConfig()
			// 1. cada entrada vive 30 s y luego Redis la borra sola
			.entryTtl(ttl)
			.disableCachingNullValues()
			// 2. el valor se guarda como JSON de CuentaVista, legible con redis-cli
			.serializeValuesWith(SerializationPair.fromSerializer(
				new Jackson2JsonRedisSerializer<>(json, CuentaVista.class)));
		return builder -> builder
			// RETO: el mismo writer de siempre, pero envuelto para que un fallo de Redis no suba hasta cuenta
			.cacheWriter(CacheWriterTolerante.de(RedisCacheWriter.nonLockingRedisCacheWriter(conexiones)))
			.withCacheConfiguration(CUENTAS, cuentas)
			// 3. el DEL de @CacheEvict espera al commit de la transaccion
			.transactionAware();
	}
}