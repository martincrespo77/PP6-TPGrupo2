package app.exception;

/** El nombre de usuario no cumple el formato o está reservado. El mensaje es para el usuario final. */
public class InvalidUsernameException extends RuntimeException {

	public InvalidUsernameException(String message) {
		super(message);
	}
}
