package mx.mexibanco.cuenta;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

/**
 * v11: la cache de GET /cuentas/{clabe} en Redis, con el patron cache-aside.
 *
 *  - @EnableCaching enciende la abstraccion de cache de Spring. Sin ella, @Cacheable y @CacheEvict
 *    se ignoran sin avisar.
 *  - cacheDeCuentas() le dice al RedisCacheManager que Spring Boot ya arma tres cosas de la cache
 *    "cuentas": cuanto vive cada entrada (mexibanco.cache.ttl, 30 s), que el valor se guarde como JSON
 *    de CuentaVista (para leerlo con redis-cli) y que sea transactionAware(): el put y el evict
 *    esperan al commit de la transaccion de Postgres.
 *  - Reto: envolverCacheManager() hace que una falla de Redis no tumbe a cuenta (ver CacheTolerante).
 *
 * Ya estaba listo: el host y el puerto de Redis (spring.data.redis) y spring.cache.type, que con
 * CACHE_TIPO=none apaga la cache sin tocar codigo.
 */
@EnableCaching
@Configuration
public class ConfiguracionCache {

	/** El nombre de la cache. En Redis cada entrada queda como la clave "cuentas::<clabe>". */
	public static final String CUENTAS = "cuentas";

	private static final Logger log = LoggerFactory.getLogger(ConfiguracionCache.class);

	@Bean
	RedisCacheManagerBuilderCustomizer cacheDeCuentas(@Value("${mexibanco.cache.ttl:30s}") Duration ttl, ObjectMapper json) {
		RedisCacheConfiguration cuentas = RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(ttl)                       // cada entrada vive 30 s y Redis la borra sola
			.disableCachingNullValues()          // un null no se guarda (una CLABE inexistente da 404, no se cachea)
			.serializeValuesWith(SerializationPair.fromSerializer(
				new Jackson2JsonRedisSerializer<>(json, CuentaVista.class)));   // JSON legible, no bytes de Java
		log.info("[cache] cache '{}' en Redis con TTL de {}", CUENTAS, ttl);
		return builder -> builder.withCacheConfiguration(CUENTAS, cuentas)
			.transactionAware();                 // put y evict esperan al commit
	}

	/**
	 * Reto (Redis se cae): envuelve el RedisCacheManager que arma Spring Boot para que cada operacion
	 * contra Redis (GET, SET, DEL) que falle se registre en el log y se trate como "no hay cache".
	 * Con CACHE_TOLERANTE=false se deja el comportamiento original, para ver el problema.
	 * Es static porque es un BeanPostProcessor: Spring lo crea antes que los demas beans.
	 */
	@Bean
	static BeanPostProcessor envolverCacheManager() {
		return new EnvolverCacheManager();
	}

	static class EnvolverCacheManager implements BeanPostProcessor, EnvironmentAware {

		private boolean tolerante = true;

		@Override
		public void setEnvironment(Environment entorno) {
			tolerante = entorno.getProperty("mexibanco.cache.tolerante", Boolean.class, true);
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String nombre) {
			if (bean instanceof RedisCacheManager redis) {
				if (!tolerante) {
					log.warn("[cache] CACHE_TOLERANTE=false: si Redis falla, la operacion falla");
					return bean;
				}
				log.info("[cache] cache tolerante a fallas de Redis encendida");
				return new CacheManagerTolerante(redis);
			}
			return bean;
		}
	}
}
