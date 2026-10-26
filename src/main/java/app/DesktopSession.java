package app;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Duración de la sesión de una computadora (D59): vence a los app.session.desktop-ttl de iniciada,
 * se use o no, y solo se extiende pidiéndolo (el aviso de la página). El vencimiento vive en la
 * sesión del servidor; SecurityConfig la cierra en el primer pedido posterior.
 */
@Component
public class DesktopSession {

	static final String EXPIRES_AT = "app.desktopSessionExpiresAt";

	/** Navegadores de celular y tablet: su sesión es la del ingreso con Google, sin este límite. */
	private static final Pattern MOBILE = Pattern.compile("Mobi|Android|iPhone|iPad|iPod", Pattern.CASE_INSENSITIVE);

	@Autowired
	private Clock clock;

	@Value("${app.session.desktop-ttl}")
	private Duration ttl;

	@Value("${app.session.warning}")
	private Duration warning;

	public static boolean isMobile(HttpServletRequest request) {
		String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
		return userAgent != null && MOBILE.matcher(userAgent).find();
	}

	public Instant start(HttpSession session) {
		Instant expiresAt = clock.instant().plus(ttl);
		session.setAttribute(EXPIRES_AT, expiresAt);
		return expiresAt;
	}

	/** @return el nuevo vencimiento, o vacío si la sesión no tiene límite */
	public Optional<Instant> extend(HttpSession session) {
		return expiresAt(session).map(current -> start(session));
	}

	public Optional<Instant> expiresAt(HttpSession session) {
		return Optional.ofNullable((Instant) session.getAttribute(EXPIRES_AT));
	}

	boolean isExpired(HttpSession session) {
		return expiresAt(session).map(expiresAt -> !clock.instant().isBefore(expiresAt)).orElse(false);
	}

	public Instant now() {
		return clock.instant();
	}

	public Duration getWarning() {
		return warning;
	}
}
