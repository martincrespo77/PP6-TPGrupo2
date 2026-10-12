package ar.edu.undef.fie.pp6.shortener.domain.exception;

/** La URL destino no cumple las reglas de validación. El mensaje es para el usuario final. */
public class InvalidUrlException extends RuntimeException {

	public InvalidUrlException(String message) {
		super(message);
	}
}
