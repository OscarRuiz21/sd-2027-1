package mx.mexibanco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Servicio transferencia de Mexi Banco v06a: un proceso, un contenedor, su propia base. */
@SpringBootApplication
@EnableScheduling
public class TransferenciaApplication {

	public static void main(String[] args) {
		SpringApplication.run(TransferenciaApplication.class, args);
	}
}
