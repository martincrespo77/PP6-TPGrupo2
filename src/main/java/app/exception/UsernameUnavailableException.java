package app.exception;

/** Otro usuario ya tiene ese nombre. */
public class UsernameUnavailableException extends RuntimeException {

	public UsernameUnavailableException(String username) {
		super("Nombre de usuario tomado: " + username);
	}
}
