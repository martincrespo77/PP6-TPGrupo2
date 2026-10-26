package app.dto;

import java.time.Duration;
import java.time.Instant;

/**
 * Duración de la sesión vista desde la página.
 *
 * @param limited true si es una sesión de computadora, que vence sola (D59)
 * @param secondsRemaining segundos hasta que se cierre, con el reloj del servidor (D24); 0 si no tiene límite
 * @param warningSeconds cuántos segundos antes de cerrarse la página ofrece extenderla
 */
public record SessionResponse(boolean limited, Instant expiresAt, long secondsRemaining, long warningSeconds) {

	public static SessionResponse of(Instant expiresAt, Instant now, Duration warning) {
		if (expiresAt == null) {
			return new SessionResponse(false, null, 0, 0);
		}
		long remaining = Math.max(0, Math.ceilDiv(Duration.between(now, expiresAt).toMillis(), 1000));
		return new SessionResponse(true, expiresAt, remaining, warning.toSeconds());
	}
}
