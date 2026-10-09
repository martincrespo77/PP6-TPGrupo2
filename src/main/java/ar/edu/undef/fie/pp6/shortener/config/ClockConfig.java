package ar.edu.undef.fie.pp6.shortener.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

	/**
	 * Truncado a milisegundos: la columna timestamp(6) guarda hasta microsegundos, y un
	 * Instant con nanosegundos volvería distinto de la base.
	 */
	@Bean
	public Clock clock() {
		return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
	}
}
