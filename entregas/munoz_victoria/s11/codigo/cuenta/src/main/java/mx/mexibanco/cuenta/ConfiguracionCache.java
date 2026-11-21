package mx.mexibanco.cuenta;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
@EnableCaching
@Configuration
public class ConfiguracionCache implements CachingConfigurer {

	private static final Logger log = LoggerFactory.getLogger(ConfiguracionCache.class);

	/** El nombre de la cache. En Redis cada entrada queda como la clave "cuentas::<clabe>". */
	public static final String CUENTAS = "cuentas";

	@Bean
	RedisCacheManagerBuilderCustomizer cacheDeCuentas(
		@Value("${mexibanco.cache.ttl:30s}") Duration ttl, ObjectMapper json) {
		RedisCacheConfiguration cuentas = RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(ttl)
			.disableCachingNullValues()
			.serializeValuesWith(SerializationPair.fromSerializer(
				new Jackson2JsonRedisSerializer<>(json, CuentaVista.class)));
		return builder -> builder.withCacheConfiguration(CUENTAS, cuentas).transactionAware();
	}

	/** La caché solo acelera la pantalla: si Redis falla, se sigue leyendo y escribiendo en Postgres. */
	@Override
	public CacheErrorHandler errorHandler() {
		return new CacheErrorHandler() {
			@Override
			public void handleCacheGetError(RuntimeException error, Cache cache, Object key) {
				log.warn("[cache] GET fallo en {} para {}; se consulta Postgres: {}", cache.getName(), key, error.toString());
			}

			@Override
			public void handleCachePutError(RuntimeException error, Cache cache, Object key, Object value) {
				log.warn("[cache] SET fallo en {} para {}; la respuesta viene de Postgres: {}", cache.getName(), key, error.toString());
			}

			@Override
			public void handleCacheEvictError(RuntimeException error, Cache cache, Object key) {
				log.warn("[cache] DEL fallo en {} para {}; Redis no pudo invalidar la copia: {}", cache.getName(), key, error.toString());
			}

			@Override
			public void handleCacheClearError(RuntimeException error, Cache cache) {
				log.warn("[cache] CLEAR fallo en {}: {}", cache.getName(), error.toString());
			}
		};
	}
}
