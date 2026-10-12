package app;

import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Compilar:  .\gradlew.bat build
// Ejecutar:  .\gradlew.bat bootRun

@SpringBootApplication
@EnableScheduling
public class Application implements WebMvcConfigurer {

	static final String[] EXTENSION_ORIGINS = { "chrome-extension://*", "moz-extension://*" };

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	/**
	 * Reloj único del sistema, en UTC. Truncado a milisegundos: la columna timestamp(6) guarda hasta
	 * microsegundos, y un Instant con nanosegundos volvería distinto de la base.
	 */
	@Bean
	public Clock clock() {
		return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
	}

	/**
	 * La extensión llama a la API desde su propio origen (D45). La web no lo necesita: se sirve
	 * desde el mismo servidor. La redirección queda afuera porque el navegador la sigue sin CORS.
	 */
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOriginPatterns(EXTENSION_ORIGINS)
				.allowedMethods("GET", "POST")
				.allowedHeaders(HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT)
				.exposedHeaders(HttpHeaders.LOCATION, HttpHeaders.RETRY_AFTER)
				.allowCredentials(false);
	}
}
