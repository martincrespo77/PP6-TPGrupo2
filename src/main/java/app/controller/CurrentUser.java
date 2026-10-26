package app.controller;

import app.mappings.AppUser;
import app.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Usuario de la sesión. El nombre de la autenticación es siempre el "sub" de Google: en la sesión de
 * Google lo pone Spring y en la de la computadora lo pone LoginRequestController al consumir el QR.
 */
@Component
class CurrentUser {

	@Autowired
	private UserService userService;

	/** Quien entró con Google en este dispositivo se da de alta acá; quien entró por QR ya existe. */
	AppUser of(Authentication authentication) {
		if (authentication.getPrincipal() instanceof OidcUser google) {
			return userService.registerIfNew(authentication.getName(), google.getEmail());
		}
		return userService.require(authentication.getName());
	}
}
