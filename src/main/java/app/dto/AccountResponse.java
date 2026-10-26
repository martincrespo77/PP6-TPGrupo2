package app.dto;

import app.mappings.AppUser;

/**
 * Datos de la cuenta con sesión iniciada.
 *
 * @param usernameChosen false mientras tenga el nombre aleatorio del alta
 * @param namespaceUrl prefijo de sus enlaces personales, por ejemplo https://dominio/martin/
 */
public record AccountResponse(String username, boolean usernameChosen, String email, String namespaceUrl) {

	public static AccountResponse from(AppUser user, String namespaceUrl) {
		return new AccountResponse(user.getUsername(), user.isUsernameChosen(), user.getEmail(), namespaceUrl);
	}
}
