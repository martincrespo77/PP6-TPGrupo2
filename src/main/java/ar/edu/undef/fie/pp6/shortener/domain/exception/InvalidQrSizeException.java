package ar.edu.undef.fie.pp6.shortener.domain.exception;

/** Tamaño de QR fuera del rango permitido (E7). El mensaje es para el usuario final. */
public class InvalidQrSizeException extends RuntimeException {

	public InvalidQrSizeException(int min, int max) {
		super("El tamaño del QR debe estar entre " + min + " y " + max + " píxeles");
	}
}
