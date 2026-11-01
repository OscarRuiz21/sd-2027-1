package mx.mexibanco.cuenta;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
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
// S11: sin @EnableCaching, @Cacheable y @CacheEvict se ignoran sin avisar.
@EnableCaching
@Configuration
public class ConfiguracionCache {

	/** El nombre de la cache. En Redis cada entrada queda como la clave "cuentas::<clabe>". */
	public static final String CUENTAS = "cuentas";

	/**
	 * S11: ajusta el RedisCacheManager que arma Spring Boot para la cache "cuentas":
	 *  1. entryTtl: cada entrada vive 30 s (mexibanco.cache.ttl) y luego Redis la borra sola;
	 *  2. el valor se guarda como JSON de CuentaVista (legible con redis-cli GET);
	 *  3. transactionAware: el DEL de @CacheEvict espera al commit de la transaccion.
	 */
	@Bean
	RedisCacheManagerBuilderCustomizer cacheDeCuentas(
			@Value("${mexibanco.cache.ttl:30s}") Duration ttl, ObjectMapper json, RedisConnectionFactory conexion) {
		RedisCacheConfiguration cuentas = RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(ttl)
			.disableCachingNullValues()
			.serializeValuesWith(SerializationPair.fromSerializer(
				new Jackson2JsonRedisSerializer<>(json, CuentaVista.class)));
		return builder -> builder.withCacheConfiguration(CUENTAS, cuentas)
			// Reto: si Redis se cae, la cache falla en silencio (log) y cuenta sigue con Postgres.
			.cacheWriter(new EscritorDeCacheTolerante(RedisCacheWriter.nonLockingRedisCacheWriter(conexion)))
			.transactionAware();
	}

	/**
	 * Reto: que Lettuce (el cliente de Redis) falle RAPIDO si Redis no esta. Por omision, con la conexion
	 * caida deja los comandos en cola mientras reconecta, y una conexion nueva espera hasta 10 s. Eso
	 * hacia que un abono ya confirmado tardara mas que el timeout de lectura de la saga (2 s).
	 *  - REJECT_COMMANDS: sin conexion, el comando falla al instante (y EscritorDeCacheTolerante lo atrapa);
	 *  - connectTimeout: una conexion nueva no espera mas de lo configurado.
	 * Sigue reconectando sola en segundo plano, asi que al volver Redis la cache regresa sin reiniciar.
	 */
	@Bean
	LettuceClientConfigurationBuilderCustomizer redisQueFallaRapido(
			@Value("${spring.data.redis.connect-timeout:500ms}") Duration conexion) {
		return builder -> builder.clientOptions(ClientOptions.builder()
			.autoReconnect(true)
			.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
			.socketOptions(SocketOptions.builder().connectTimeout(conexion).build())
			.build());
	}
}
