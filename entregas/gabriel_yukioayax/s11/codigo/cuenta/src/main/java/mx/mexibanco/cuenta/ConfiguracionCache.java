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
 * v11: la cache de GET /cuentas/{clabe} en Redis, con el patron cache-aside.
 *
 * Spring Boot ya arma el RedisCacheManager (por spring-boot-starter-data-redis); aqui se enciende la
 * abstraccion de cache y se le cambian tres cosas a la cache "cuentas":
 *  1. cada entrada vive mexibanco.cache.ttl (30 s) y luego Redis la borra sola;
 *  2. el valor se guarda como JSON de CuentaVista, legible con redis-cli GET;
 *  3. transactionAware(): el DEL de @CacheEvict espera al commit de la transaccion.
 *
 * Reto (Redis se cae): el que habla con Redis es el RedisCacheWriter. Se envuelve en
 * CacheWriterTolerante para que, si Redis no contesta, el GET vaya a Postgres y el DEL que corre
 * DESPUES del commit no convierta en 500 un cargo que ya se confirmo.
 */
// sin @EnableCaching, @Cacheable y @CacheEvict se ignoran sin avisar
@EnableCaching
@Configuration
public class ConfiguracionCache {

	/** El nombre de la cache. En Redis cada entrada queda como la clave "cuentas::<clabe>". */
	public static final String CUENTAS = "cuentas";

	@Bean
	RedisCacheManagerBuilderCustomizer cacheDeCuentas(@Value("${mexibanco.cache.ttl:30s}") Duration ttl,
													  ObjectMapper json, RedisConnectionFactory conexiones) {
		RedisCacheConfiguration cuentas = RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(ttl)                       // cada entrada vive 30 s
			.disableCachingNullValues()
			.serializeValuesWith(SerializationPair.fromSerializer(
				new Jackson2JsonRedisSerializer<>(json, CuentaVista.class)));   // JSON legible
		return builder -> builder
			.cacheWriter(new CacheWriterTolerante(RedisCacheWriter.nonLockingRedisCacheWriter(conexiones)))
			.withCacheConfiguration(CUENTAS, cuentas)
			.transactionAware();                 // put y evict esperan al commit
	}
}
