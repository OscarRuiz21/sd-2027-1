package mx.mexibanco.clientes;

import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;

@Configuration
public class ResilienciaConfig {

        @Bean
        public CircuitBreakerManual cuentaCircuitBreaker(
                        @Value("${mexibanco.circuitbreaker.cuenta.umbral-fallas:3}") int umbralFallas,
                        @Value("${mexibanco.circuitbreaker.cuenta.espera-abierto-s:10}") long esperaAbiertoS,
                        @Value("${mexibanco.circuitbreaker.cuenta.pruebas-semiabierto:5}") int pruebas) {
                return new CircuitBreakerManual("cuenta", umbralFallas, Duration.ofSeconds(esperaAbiertoS),
                        pruebas, ResilienciaConfig::esFallaDeInfraestructura);
        }

        /** 404 y 422 son respuestas de negocio: cuenta esta viva y no cuentan como falla. */
        private static boolean esFallaDeInfraestructura(RuntimeException e) {
                return !(e instanceof NoEncontradoException)
                        && !(e instanceof ReglaDeNegocioException)
                        && !(e instanceof HttpClientErrorException);
        }
}
