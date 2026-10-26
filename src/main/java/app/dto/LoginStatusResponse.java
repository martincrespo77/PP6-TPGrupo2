package app.dto;

import java.time.Duration;
import java.time.Instant;

/**
 * Estado del ingreso por QR visto desde la computadora.
 *
 * @param status PENDING (esperando el celular), APPROVED (sesión iniciada) o EXPIRED (pedir otro código)
 * @param secondsRemaining segundos hasta que venza el código; 0 si ya no aplica
 */
public record LoginStatusResponse(String status, long secondsRemaining) {

	public static LoginStatusResponse of(String status, Instant expiresAt, Instant now) {
		long remaining = expiresAt == null ? 0 : Math.max(0, Math.ceilDiv(Duration.between(now, expiresAt).toMillis(), 1000));
		return new LoginStatusResponse(status, remaining);
	}
}
