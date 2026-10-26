package app.controller;

import app.mappings.AppUser;
import app.service.LinkService;
import app.service.PersonalLinkService;
import app.service.UserService;
import io.swagger.v3.oas.annotations.Hidden;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.util.HtmlUtils;

/**
 * GET /{nombre} y GET /{usuario}/{alias}: 302 al destino o 404 con la página "Este enlace expiró o no
 * existe" (D7, D20). /{nombre} es la página de un usuario si es su nombre, y si no un alias público:
 * no hay ambigüedad porque el generador de alias saltea los nombres en uso (D54).
 * El patrón no admite puntos ni barras, así que no choca con /api/** ni con archivos estáticos (D15).
 */
@Hidden
@Controller
public class RedirectController {

	private static final String NOT_AVAILABLE_PAGE = "static/enlace-no-disponible.html";
	private static final String MOVED_PAGE = "static/enlace-movido.html";
	private static final String ACCOUNT_PAGE = "static/cuenta.html";
	private static final Pattern PUBLIC_ALIAS = Pattern.compile("[A-Za-z0-9]{1,16}");

	@Autowired
	private LinkService linkService;

	@Autowired
	private PersonalLinkService personalLinkService;

	@Autowired
	private UserService userService;

	private final byte[] notAvailablePage = load(NOT_AVAILABLE_PAGE);
	private final String movedPage = new String(load(MOVED_PAGE), StandardCharsets.UTF_8);
	private final byte[] accountPage = load(ACCOUNT_PAGE);

	/** Página del usuario (acortador y perfil); con su nombre anterior reservado lleva al nuevo (D57). */
	@GetMapping("/{name:[A-Za-z0-9-]{1,20}}")
	public ResponseEntity<byte[]> redirect(@PathVariable String name) {
		if (userService.findByUsername(name).isPresent()) {
			return html(HttpStatus.OK, accountPage);
		}
		Optional<AppUser> renamed = userService.findByReservedPreviousUsername(name);
		if (renamed.isPresent()) {
			return ResponseEntity.status(HttpStatus.FOUND)
					.location(URI.create("/" + renamed.get().getUsername()))
					.cacheControl(CacheControl.noStore())
					.build();
		}
		if (!PUBLIC_ALIAS.matcher(name).matches()) {
			return html(HttpStatus.NOT_FOUND, notAvailablePage);
		}
		return redirectOrNotAvailable(linkService.resolve(name));
	}

	/**
	 * Enlace personal. Dos segmentos: no choca con /{nombre}. Con el nombre anterior de su dueño, todavía
	 * reservado, muestra un aviso con la dirección nueva y después sigue al destino (D56).
	 */
	@GetMapping("/{username:[A-Za-z0-9-]{3,20}}/{alias:[A-Za-z0-9-]{1,32}}")
	public ResponseEntity<byte[]> redirectPersonal(@PathVariable String username, @PathVariable String alias) {
		Optional<String> target = personalLinkService.resolve(username, alias);
		if (target.isPresent()) {
			return redirectOrNotAvailable(target);
		}
		return personalLinkService.findMoved(username, alias)
				.map(moved -> html(HttpStatus.OK, movedPage
						.replace("{{newShortUrl}}", HtmlUtils.htmlEscape(moved.newShortUrl()))
						.replace("{{targetUrl}}", HtmlUtils.htmlEscape(moved.targetUrl()))
						.replace("{{oldNameReleasedAt}}", moved.oldNameReleasedAt().toString())
						.getBytes(StandardCharsets.UTF_8)))
				.orElseGet(() -> html(HttpStatus.NOT_FOUND, notAvailablePage));
	}

	private ResponseEntity<byte[]> redirectOrNotAvailable(Optional<String> target) {
		return target
				.map(url -> ResponseEntity.status(HttpStatus.FOUND)
						.location(URI.create(url))
						.cacheControl(CacheControl.noStore())
						.<byte[]>build())
				.orElseGet(() -> html(HttpStatus.NOT_FOUND, notAvailablePage));
	}

	private static ResponseEntity<byte[]> html(HttpStatus status, byte[] page) {
		return ResponseEntity.status(status)
				.contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
				.cacheControl(CacheControl.noStore())
				.body(page);
	}

	private static byte[] load(String path) {
		try (var in = new ClassPathResource(path).getInputStream()) {
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("No se encontró " + path, e);
		}
	}
}
