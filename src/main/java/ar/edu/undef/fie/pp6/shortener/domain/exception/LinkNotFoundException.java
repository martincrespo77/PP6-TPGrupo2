package ar.edu.undef.fie.pp6.shortener.domain.exception;

/** El alias no existe o venció; los dos casos son indistinguibles a propósito (D7). */
public class LinkNotFoundException extends RuntimeException {

	public LinkNotFoundException(String alias) {
		super("Enlace no disponible: " + alias);
	}
}
