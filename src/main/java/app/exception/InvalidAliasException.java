package app.exception;

/** El alias personalizado no cumple el formato. El mensaje es para el usuario final. */
public class InvalidAliasException extends RuntimeException {

	public InvalidAliasException(String message) {
		super(message);
	}
}
