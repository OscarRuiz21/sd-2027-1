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
 * Que va aqui (lab S11, Parte 1):
 *  - encender la abstraccion de cache de Spring;
 *  - decirle al RedisCacheManager que Spring Boot ya arma (por spring-boot-starter-data-redis) tres
 *    cosas de la cache "cuentas": cuanto vive cada entrada (mexibanco.cache.ttl, 30 s), que el valor
 *    se guarde como JSON (para leerlo con redis-cli) y que sea transactionAware(), para que el borrado
 *    ocurra DESPUES del commit y no antes.
 *
 * Lo que ya esta listo: el host y el puerto de Redis (spring.data.redis en application.yml) y
 * spring.cache.type, que con CACHE_TIPO=none apaga la cache sin tocar codigo.
 */
// Lab S11: sin @EnableCaching, @Cacheable y @CacheEvict se ignoran sin avisar.
@EnableCaching
@Configuration
public class ConfiguracionCache {

	/** El nombre de la cache. En Redis cada entrada queda como la clave "cuentas::<clabe>". */
	public static final String CUENTAS = "cuentas";

	/**
	 * Lab S11: Spring Boot ya arma el RedisCacheManager; aqui solo se le cambian tres cosas a la cache
	 * CUENTAS. El ObjectMapper es el de Spring (ya sabe escribir el Instant de leidaEn).
	 */
	@Bean
	RedisCacheManagerBuilderCustomizer cacheDeCuentas(@Value("${mexibanco.cache.ttl:30s}") Duration ttl, ObjectMapper json,
													  RedisConnectionFactory conexion) {
		RedisCacheConfiguration cuentas = RedisCacheConfiguration.defaultCacheConfig()
			// 1. cada entrada vive 30 s y luego Redis la borra sola (si alguien cambia Postgres por fuera,
			//    el dato viejo no se queda para siempre)
			.entryTtl(ttl)
			.disableCachingNullValues()
			// 2. el valor se guarda como JSON de CuentaVista, legible con redis-cli GET
			.serializeValuesWith(SerializationPair.fromSerializer(
				new Jackson2JsonRedisSerializer<>(json, CuentaVista.class)));
		return builder -> builder.withCacheConfiguration(CUENTAS, cuentas)
			// 3. el put y el DEL de @CacheEvict esperan al commit de la transaccion
			.transactionAware()
			// Reto: si Redis se cae, la cache se comporta como vacia en lugar de tumbar a cuenta
			// (ver CacheWriterTolerante: atrapa tambien el DEL que corre despues del commit).
			.cacheWriter(new CacheWriterTolerante(RedisCacheWriter.nonLockingRedisCacheWriter(conexion)));
	}
}
