package app.exception;

/** El usuario todavía tiene el nombre aleatorio del alta: tiene que elegir uno antes de crear enlaces. */
public class UsernameNotChosenException extends RuntimeException {

	public UsernameNotChosenException() {
		super("El usuario todavía no eligió su nombre");
	}
}
