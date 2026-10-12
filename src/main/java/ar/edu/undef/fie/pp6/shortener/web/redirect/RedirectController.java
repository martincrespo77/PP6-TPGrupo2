package ar.edu.undef.fie.pp6.shortener.web.redirect;

import ar.edu.undef.fie.pp6.shortener.application.ResolveLinkService;
import io.swagger.v3.oas.annotations.Hidden;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * GET /{alias}: 302 al destino o 404 con la página "Este enlace expiró o no existe" (D7, D20).
 * El patrón no admite puntos ni barras, así que no choca con /api/** ni con archivos estáticos (D15).
 */
@Hidden
@Controller
public class RedirectController {

	private static final String NOT_AVAILABLE_PAGE = "static/enlace-no-disponible.html";

	private final ResolveLinkService resolveLinkService;
	private final byte[] notAvailablePage;

	public RedirectController(ResolveLinkService resolveLinkService) {
		this.resolveLinkService = resolveLinkService;
		this.notAvailablePage = load(NOT_AVAILABLE_PAGE);
	}

	@GetMapping("/{alias:[A-Za-z0-9]{1,16}}")
	public ResponseEntity<byte[]> redirect(@PathVariable String alias) {
		return resolveLinkService.resolve(alias)
				.map(target -> ResponseEntity.status(HttpStatus.FOUND)
						.location(URI.create(target))
						.cacheControl(CacheControl.noStore())
						.<byte[]>build())
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
						.contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
						.cacheControl(CacheControl.noStore())
						.body(notAvailablePage));
	}

	private static byte[] load(String path) {
		try (var in = new ClassPathResource(path).getInputStream()) {
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("No se encontró " + path, e);
		}
	}
}
