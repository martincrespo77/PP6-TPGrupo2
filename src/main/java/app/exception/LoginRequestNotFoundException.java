package app.exception;

/** El código de ingreso no existe, venció o ya se usó; los casos no se distinguen a propósito. */
public class LoginRequestNotFoundException extends RuntimeException {

	public LoginRequestNotFoundException() {
		super("Código de ingreso no disponible");
	}
}
