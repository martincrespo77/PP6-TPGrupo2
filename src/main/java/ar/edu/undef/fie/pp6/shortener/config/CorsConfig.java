package ar.edu.undef.fie.pp6.shortener.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * La extensión llama a la API desde su propio origen (D45). La web no lo necesita: se sirve
 * desde el mismo servidor. La redirección queda afuera porque el navegador la sigue sin CORS.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	static final String[] EXTENSION_ORIGINS = { "chrome-extension://*", "moz-extension://*" };

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
