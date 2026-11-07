package mx.mexibanco.cuenta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Reto S11 (Redis se cae): envuelve al RedisCacheWriter, que es la pieza que de verdad habla con Redis,
 * para que un fallo de Redis NUNCA llegue hasta cuenta.
 *
 * Por que aqui y no en un CacheErrorHandler: con transactionAware(), el DEL de @CacheEvict corre en
 * afterCommit, FUERA del interceptor de cache, y el CacheErrorHandler no lo ve. Si ese DEL lanzara una
 * excepcion, saldria del commit: cuenta contestaria 500 a un cargo que YA se confirmo, y la saga creeria
 * que el cargo fallo. El writer es el unico punto por el que pasan todas las operaciones, incluido ese DEL.
 *
 * Que hace con cada fallo:
 *  - get    -> regresa null: para Spring es un MISS, corre el metodo y lee Postgres;
 *  - put    -> se ignora: no se guardo la copia, la proxima consulta volvera a leer Postgres;
 *  - remove -> se ignora: si Redis esta caido no hay copia que borrar (y al volver arranca vacio);
 *  - y siempre deja una linea en el log que dice QUE operacion fallo.
 *
 * Es un proxy dinamico del interfaz, asi no depende de que metodos tenga RedisCacheWriter en cada version.
 */
public final class CacheWriterTolerante implements InvocationHandler {

	private static final Logger log = LoggerFactory.getLogger(CacheWriterTolerante.class);

	private final RedisCacheWriter real;

	private CacheWriterTolerante(RedisCacheWriter real) {
		this.real = real;
	}

	public static RedisCacheWriter de(RedisCacheWriter real) {
		return (RedisCacheWriter) Proxy.newProxyInstance(
			RedisCacheWriter.class.getClassLoader(),
			new Class<?>[]{RedisCacheWriter.class},
			new CacheWriterTolerante(real));
	}

	@Override
	public Object invoke(Object proxy, Method metodo, Object[] args) throws Throwable {
		try {
			Object resultado = metodo.invoke(real, args);
			if (resultado instanceof RedisCacheWriter otro && metodo.getReturnType() == RedisCacheWriter.class) {
				return de(otro);    // por ejemplo withStatisticsCollector: que siga siendo tolerante
			}
			return resultado;
		} catch (InvocationTargetException envuelta) {
			Throwable causa = envuelta.getTargetException();
			if (causa instanceof RuntimeException falloDeRedis) {
				log.warn("[cache] Redis fallo en {} {}: {}. Se sigue sin cache.",
					metodo.getName(), que(args), falloDeRedis.getMessage());
				return valorSeguro(metodo.getReturnType());
			}
			throw causa;
		}
	}

	/** La cache y la clave de la operacion, para que el log diga QUE fallo (ej. cuentas cuentas::0021...). */
	private static String que(Object[] args) {
		if (args == null || args.length == 0) {
			return "";
		}
		String cache = String.valueOf(args[0]);
		if (args.length > 1 && args[1] instanceof byte[] clave) {
			return cache + " " + new String(clave, StandardCharsets.UTF_8);
		}
		return cache;
	}

	/** Lo que se contesta cuando Redis fallo: nada (null), que Spring interpreta como "no esta en la cache". */
	private static Object valorSeguro(Class<?> tipo) {
		if (tipo == void.class) {
			return null;
		}
		if (tipo == CompletableFuture.class) {
			return CompletableFuture.completedFuture(null);
		}
		if (tipo == boolean.class) {
			return false;
		}
		if (tipo.isPrimitive()) {
			return 0;
		}
		return null;
	}
}