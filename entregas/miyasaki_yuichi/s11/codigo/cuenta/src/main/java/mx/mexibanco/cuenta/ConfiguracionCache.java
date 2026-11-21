package mx.mexibanco.cuenta;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

// sin @EnableCaching, @Cacheable y @CacheEvict se ignoran sin avisar
@EnableCaching
@Configuration
public class ConfiguracionCache implements CachingConfigurer {

	/** El nombre de la cache. En Redis cada entrada queda como la clave "cuentas::<clabe>". */
	public static final String CUENTAS = "cuentas";

	private final BeanFactory beans;

	public ConfiguracionCache(BeanFactory beans) {
		this.beans = beans;
	}

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
		// 3. Reto: ya NO va .transactionAware() aqui; lo pone cacheResolver() por fuera de CacheTolerante
		return builder -> builder.withCacheConfiguration(CUENTAS, cuentas);
	}

	/**
	 * Reto: cada cache que usan @Cacheable y @CacheEvict queda asi:
	 *   TransactionAwareCacheDecorator -> CacheTolerante -> RedisCache
	 * El DEL sigue esperando al commit y, cuando corre, CacheTolerante atrapa el error de Redis.
	 */
	@Override
	public CacheResolver cacheResolver() {
		return contexto -> contexto.getOperation().getCacheNames().stream()
			.map(this::envolver)
			.toList();
	}

	private Cache envolver(String nombre) {
		// "cacheManager" es el que arma Spring Boot: RedisCacheManager, o NoOp con CACHE_TIPO=none
		CacheManager manager = beans.getBean("cacheManager", CacheManager.class);
		return new TransactionAwareCacheDecorator(new CacheTolerante(manager.getCache(nombre)));
	}
}