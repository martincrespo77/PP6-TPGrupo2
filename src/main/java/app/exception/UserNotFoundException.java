package app.exception;

/** La sesión pertenece a un usuario que ya no está en la base (por ejemplo, después de borrarla). */
public class UserNotFoundException extends RuntimeException {

	public UserNotFoundException() {
		super("Usuario no registrado");
	}
}
