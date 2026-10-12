package ar.edu.undef.fie.pp6.shortener.infrastructure.validation;

import ar.edu.undef.fie.pp6.shortener.domain.exception.InvalidUrlException;
import ar.edu.undef.fie.pp6.shortener.domain.model.ShortLink;
import ar.edu.undef.fie.pp6.shortener.domain.port.UrlValidator;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Acepta URLs absolutas http/https con host (D16-D19). No filtra por destino:
 * el propio dominio, localhost e IPs privadas son válidos (C1).
 */
@Component
public class RegexUrlValidator implements UrlValidator {

	static final String EMPTY = "Ingresá la dirección que querés acortar";
	static final String TOO_LONG = "La dirección no puede superar los " + ShortLink.MAX_URL_LENGTH + " caracteres";
	static final String MISSING_SCHEME = "La dirección debe empezar con http:// o https://";
	static final String INVALID_FORMAT = "La dirección no tiene un formato válido";

	private static final int MAX_PORT = 65_535;
	private static final Pattern HTTP_SCHEME = Pattern.compile("^https?://.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	@Override
	public void validate(String url) {
		if (url == null || url.isBlank()) {
			throw new InvalidUrlException(EMPTY);
		}
		if (url.length() > ShortLink.MAX_URL_LENGTH) {
			throw new InvalidUrlException(TOO_LONG);
		}
		if (!HTTP_SCHEME.matcher(url).matches()) {
			throw new InvalidUrlException(MISSING_SCHEME);
		}
		if (!hasValidHost(url)) {
			throw new InvalidUrlException(INVALID_FORMAT);
		}
	}

	private static boolean hasValidHost(String url) {
		try {
			URI uri = new URI(url).parseServerAuthority();
			String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
			return (scheme.equals("http") || scheme.equals("https"))
					&& uri.getHost() != null
					&& uri.getPort() <= MAX_PORT;
		} catch (URISyntaxException e) {
			return false;
		}
	}
}
