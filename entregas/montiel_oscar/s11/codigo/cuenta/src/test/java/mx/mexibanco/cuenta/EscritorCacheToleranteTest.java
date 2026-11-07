package mx.mexibanco.cuenta;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EscritorCacheToleranteTest {
    private final RedisCacheWriter delegado = mock(RedisCacheWriter.class);
    private final EscritorCacheTolerante seguro = new EscritorCacheTolerante(delegado);

    @Test
    void unFalloDeRedisSeConvierteEnMissYOmitirEscrituras() {
        when(delegado.get(anyString(), any())).thenThrow(new RedisConnectionFailureException("apagado"));
        doThrow(new RedisConnectionFailureException("apagado")).when(delegado).put(anyString(), any(), any(), any());
        assertThat(seguro.get("cuentas", new byte[]{1})).isNull();
        assertThatCode(() -> seguro.put("cuentas", new byte[]{1}, new byte[]{2}, Duration.ofSeconds(30)))
            .doesNotThrowAnyException();
    }

    @Test
    void unDelFallidoDespuesDelCommitNoPropagaUnFalsoErrorDeCargo() {
        doThrow(new RedisConnectionFailureException("apagado")).when(delegado).remove(anyString(), any());
        RedisCacheManager manager = RedisCacheManager.builder(seguro)
            .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()).transactionAware().build();
        manager.afterPropertiesSet();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            manager.getCache("cuentas").evict("ana");
            verify(delegado, never()).remove(anyString(), any());
            assertThatCode(() -> {
                for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) s.afterCommit();
            }).doesNotThrowAnyException();
            verify(delegado).remove(anyString(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void unErrorDeProgramacionNoSeOcultaComoCaidaDeRedis() {
        when(delegado.get(anyString(), any())).thenThrow(new IllegalArgumentException("clave invalida"));
        assertThatThrownBy(() -> seguro.get("cuentas", new byte[]{1})).isInstanceOf(IllegalArgumentException.class);
    }
}
