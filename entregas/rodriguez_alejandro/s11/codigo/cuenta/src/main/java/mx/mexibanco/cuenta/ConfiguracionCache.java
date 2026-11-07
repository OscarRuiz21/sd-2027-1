package mx.mexibanco.cuenta;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

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
@Configuration
@EnableCaching
public class ConfiguracionCache implements CachingConfigurer {

	/** El nombre de la cache. En Redis cada entrada queda como la clave "cuentas::<clabe>". */
	public static final String CUENTAS = "cuentas";
	private static final Logger log = LoggerFactory.getLogger(ConfiguracionCache.class);

	@Bean
	public RedisCacheManagerBuilderCustomizer cacheDeCuentas(@Value("${mexibanco.cache.ttl:30s}") Duration ttl, ObjectMapper json) {
		RedisCacheConfiguration cuentas = RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(ttl)
			.disableCachingNullValues()
			.serializeValuesWith(SerializationPair.fromSerializer(
				new Jackson2JsonRedisSerializer<>(json, CuentaVista.class)));
		
		return builder -> builder.withCacheConfiguration(CUENTAS, cuentas)
			.transactionAware();
	}

	@Override
	public CacheErrorHandler errorHandler() {
		return new SimpleCacheErrorHandler() {
			@Override
			public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
				log.warn("[Cache] Falló GET en caché para clave {}. Ignorando y leyendo de Postgres.", key);
			}
			@Override
			public void handleCachePutError(RuntimeException exception, org.springframework.cache.Cache cache, Object key, Object value) {
				log.warn("[Cache] Falló PUT en caché para clave {}.", key);
			}
			@Override
			public void handleCacheEvictError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
				log.warn("[Cache] Falló EVICT en caché para clave {}. Riesgo de dato viejo si Redis vuelve.", key);
			}
			@Override
			public void handleCacheClearError(RuntimeException exception, org.springframework.cache.Cache cache) {
				log.warn("[Cache] Falló CLEAR.");
			}
		};
	}
}