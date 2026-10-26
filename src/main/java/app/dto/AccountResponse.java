package app.dto;

import app.mappings.AppUser;
import java.time.Duration;
import java.time.Instant;

/**
 * Datos de la cuenta con sesión iniciada.
 *
 * @param usernameChosen false mientras tenga el nombre aleatorio del alta
 * @param namespaceUrl prefijo de sus enlaces personales, por ejemplo https://dominio/martin/
 * @param previousUsername nombre anterior todavía reservado para esta cuenta, o null (D55)
 * @param previousUsernameReleasedAt cuándo se libera el nombre anterior, o null
 * @param secondsUntilPreviousReleased segundos hasta liberarlo, con el reloj del servidor (D24)
 */
public record AccountResponse(String username, boolean usernameChosen, String email, String namespaceUrl,
		String previousUsername, Instant previousUsernameReleasedAt, long secondsUntilPreviousReleased) {

	public static AccountResponse from(AppUser user, String namespaceUrl, Instant now) {
		if (!user.hasReservedPreviousUsername(now)) {
			return new AccountResponse(user.getUsername(), user.isUsernameChosen(), user.getEmail(), namespaceUrl, null, null, 0);
		}
		long remaining = Math.ceilDiv(Duration.between(now, user.getPreviousUsernameReleasedAt()).toMillis(), 1000);
		return new AccountResponse(user.getUsername(), user.isUsernameChosen(), user.getEmail(), namespaceUrl,
				user.getPreviousUsername(), user.getPreviousUsernameReleasedAt(), remaining);
	}
}
