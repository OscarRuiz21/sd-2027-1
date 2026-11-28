package mx.mexibanco.cuenta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

/**
 * v11, reto (Redis se cae): una cache que NUNCA deja que una falla de Redis se convierta en un error
 * de cuenta. La cache era solo una copia para mostrar; no debe poder tumbar nada.
 *
 *  - GET que falla: se trata como un miss (regresa null) y la consulta va a Postgres.
 *  - SET que falla: no se guarda la copia; la respuesta ya salio de Postgres.
 *  - DEL que falla: la copia vieja puede quedarse hasta que venza su TTL (30 s). Es el riesgo que se
 *    acepta, y queda en el log.
 *
 * Por que aqui y no en un CacheErrorHandler: con transactionAware() el DEL no ocurre dentro de
 * @CacheEvict, sino DESPUES del commit, en un TransactionSynchronization.afterCommit(). Ahi ya no hay
 * CacheErrorHandler que lo atrape: la excepcion sale hasta el controller y cuenta contestaria 500 a un
 * cargo que YA se confirmo en Postgres. La saga de transferencia lo tomaria como "el cargo no se
 * hizo" y el dinero quedaria fuera de origen sin devolverse. Envolviendo la cache real (por dentro del
 * decorador transaccional, ver CacheManagerTolerante) la falla se atrapa tambien en ese momento.
 */
public class CacheTolerante implements Cache {

	private static final Logger log = LoggerFactory.getLogger(CacheTolerante.class);

	private final Cache real;

	public CacheTolerante(Cache real) {
		this.real = real;
	}

	@Override
	public String getName() {
		return real.getName();
	}

	@Override
	public Object getNativeCache() {
		return real.getNativeCache();
	}

	@Override
	public ValueWrapper get(Object clave) {
		try {
			return real.get(clave);
		} catch (RuntimeException falla) {
			avisar("GET", clave, falla, "se lee de Postgres");
			return null;
		}
	}

	@Override
	public <T> T get(Object clave, Class<T> tipo) {
		try {
			return real.get(clave, tipo);
		} catch (RuntimeException falla) {
			avisar("GET", clave, falla, "se lee de Postgres");
			return null;
		}
	}

	@Override
	public <T> T get(Object clave, Callable<T> cargador) {
		try {
			return real.get(clave, cargador);
		} catch (ValueRetrievalException fallaDelCargador) {
			throw fallaDelCargador;                    // fallo Postgres (o no existe la CLABE): no es de Redis
		} catch (RuntimeException falla) {
			avisar("GET", clave, falla, "se lee de Postgres");
			try {
				return cargador.call();
			} catch (Exception e) {
				throw new ValueRetrievalException(clave, cargador, e);
			}
		}
	}

	@Override
	public void put(Object clave, Object valor) {
		try {
			real.put(clave, valor);
		} catch (RuntimeException falla) {
			avisar("SET", clave, falla, "no se guarda la copia");
		}
	}

	@Override
	public ValueWrapper putIfAbsent(Object clave, Object valor) {
		try {
			return real.putIfAbsent(clave, valor);
		} catch (RuntimeException falla) {
			avisar("SET", clave, falla, "no se guarda la copia");
			return null;
		}
	}

	@Override
	public void evict(Object clave) {
		try {
			real.evict(clave);
		} catch (RuntimeException falla) {
			avisar("DEL", clave, falla, "la copia vieja puede durar hasta su TTL");
		}
	}

	@Override
	public boolean evictIfPresent(Object clave) {
		try {
			return real.evictIfPresent(clave);
		} catch (RuntimeException falla) {
			avisar("DEL", clave, falla, "la copia vieja puede durar hasta su TTL");
			return false;
		}
	}

	@Override
	public void clear() {
		try {
			real.clear();
		} catch (RuntimeException falla) {
			avisar("CLEAR", "*", falla, "las copias viejas pueden durar hasta su TTL");
		}
	}

	@Override
	public boolean invalidate() {
		try {
			return real.invalidate();
		} catch (RuntimeException falla) {
			avisar("CLEAR", "*", falla, "las copias viejas pueden durar hasta su TTL");
			return false;
		}
	}

	private void avisar(String operacion, Object clave, RuntimeException falla, String consecuencia) {
		log.warn("[cache] Redis no disponible: fallo {} {}::{} ({}); {}", operacion, getName(), clave,
			falla.getClass().getSimpleName(), consecuencia);
	}
}
