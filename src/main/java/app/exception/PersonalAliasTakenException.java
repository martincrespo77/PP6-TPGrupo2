package app.exception;

/** El usuario ya tiene un enlace vigente con ese alias. */
public class PersonalAliasTakenException extends RuntimeException {

	public PersonalAliasTakenException(String alias) {
		super("Alias en uso: " + alias);
	}
}
