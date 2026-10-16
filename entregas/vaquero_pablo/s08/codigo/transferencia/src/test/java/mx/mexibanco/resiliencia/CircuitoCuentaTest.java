package mx.mexibanco.resiliencia;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static mx.mexibanco.resiliencia.CircuitoCuenta.Estado.*;

class CircuitoCuentaTest {
    final AtomicLong reloj = new AtomicLong();
    final CircuitoCuenta cb = new CircuitoCuenta(3,1000,reloj::get);
    void falla() {
        assertThatThrownBy(() -> cb.llamar(() -> { throw new RuntimeException("caida"); })).isInstanceOf(RuntimeException.class);
    }
    void abrir() { falla(); falla(); falla(); }

    @Test void abreTrasTresFallasYRechazaSinEjecutar() {
        abrir();
        assertThat(cb.consultar().estado()).isEqualTo(ABIERTO);
        assertThatThrownBy(() -> cb.llamar(() -> { throw new AssertionError("no debe llamarse"); }))
            .isInstanceOf(CircuitoAbiertoException.class);
        assertThat(cb.consultar().llamadasPermitidas()).isEqualTo(3);
        assertThat(cb.consultar().llamadasRechazadas()).isEqualTo(1);
    }
    @Test void unaRespuestaCorrectaReiniciaFallasConsecutivas() {
        falla(); falla(); cb.llamar(() -> "ok"); falla();
        assertThat(cb.consultar().estado()).isEqualTo(CERRADO);
        assertThat(cb.consultar().fallasConsecutivas()).isEqualTo(1);
    }
    @Test void erroresDeNegocioNoAbrenCircuito() {
        for (int i=0;i<5;i++)
            assertThatThrownBy(() -> cb.llamar(() -> { throw new ErrorRemotoException("cuenta",422,"saldo"); }))
                .isInstanceOf(ErrorRemotoException.class);
        assertThat(cb.consultar().estado()).isEqualTo(CERRADO);
        assertThat(cb.consultar().fallasConsecutivas()).isZero();
    }
    @Test void recuperacionPasaPorSemiabiertoYCierra() {
        abrir(); reloj.set(1_000_000_000L);
        assertThat(cb.llamar(() -> cb.consultar().estado())).isEqualTo(SEMIABIERTO);
        assertThat(cb.consultar().estado()).isEqualTo(CERRADO);
        assertThat(cb.consultar().cambios()).extracting(CircuitoCuenta.Cambio::hacia)
            .containsExactly(ABIERTO,SEMIABIERTO,CERRADO);
    }
    @Test void pruebaFallidaReabreYReiniciaEspera() {
        abrir(); reloj.set(1_000_000_000L); falla();
        assertThat(cb.consultar().estado()).isEqualTo(ABIERTO);
        reloj.set(1_999_000_000L);
        assertThatThrownBy(() -> cb.llamar(() -> "ok")).isInstanceOf(CircuitoAbiertoException.class);
        reloj.set(2_000_000_000L);
        assertThat(cb.llamar(() -> "ok")).isEqualTo("ok");
    }
    @Test void semiabiertoPermiteUnaSolaPruebaConcurrente() throws Exception {
        abrir(); reloj.set(1_000_000_000L);
        CountDownLatch dentro = new CountDownLatch(1), salir = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<String> f = pool.submit(() -> cb.llamar(() -> {
                dentro.countDown();
                try { if (!salir.await(2,TimeUnit.SECONDS)) throw new AssertionError("timeout de prueba"); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                return "ok";
            }));
            try {
                assertThat(dentro.await(2,TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> cb.llamar(() -> "otra")).isInstanceOf(CircuitoAbiertoException.class);
            } finally { salir.countDown(); }
            assertThat(f.get(2,TimeUnit.SECONDS)).isEqualTo("ok");
        }
        assertThat(cb.consultar().estado()).isEqualTo(CERRADO);
    }
    @Test void respuestaViejaNoCierraUnCircuitoRecienAbierto() throws Exception {
        CountDownLatch dentro = new CountDownLatch(1), salir = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<String> f = pool.submit(() -> cb.llamar(() -> {
                dentro.countDown();
                try { if (!salir.await(2,TimeUnit.SECONDS)) throw new AssertionError("timeout de prueba"); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                return "respuesta vieja";
            }));
            try { assertThat(dentro.await(2,TimeUnit.SECONDS)).isTrue(); abrir(); }
            finally { salir.countDown(); }
            f.get(2,TimeUnit.SECONDS);
        }
        assertThat(cb.consultar().estado()).isEqualTo(ABIERTO);
    }
}
