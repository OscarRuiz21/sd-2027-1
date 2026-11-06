package mx.mexibanco.cuenta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public final class EscritorRedisTolerante {

    private static final Logger log =
            LoggerFactory.getLogger(EscritorRedisTolerante.class);

    private EscritorRedisTolerante() {
    }

    public static RedisCacheWriter envolver(RedisCacheWriter real) {
        return (RedisCacheWriter) Proxy.newProxyInstance(
                RedisCacheWriter.class.getClassLoader(),
                new Class<?>[]{RedisCacheWriter.class},
                (proxy, metodo, argumentos) -> {
                    String operacion = metodo.getName();

                    if (operacion.equals("supportsAsyncRetrieve")) {
                        return false;
                    }

                    try {
                        Object resultado = metodo.invoke(real, argumentos);

                        if (operacion.equals("withStatisticsCollector")) {
                            return envolver((RedisCacheWriter) resultado);
                        }

                        return resultado;
                    } catch (InvocationTargetException error) {
                        Throwable causa = error.getCause();

                        boolean operacionDeCache = switch (operacion) {
                            case "get", "put", "putIfAbsent",
                                 "remove", "clean" -> true;
                            default -> false;
                        };

                        if (operacionDeCache
                                && causa instanceof RuntimeException) {
                            log.warn(
                                    "[cache] fallo Redis en {}: {}",
                                    operacion,
                                    causa.toString()
                            );

                            return null;
                        }

                        throw causa;
                    }
                }
        );
    }
}