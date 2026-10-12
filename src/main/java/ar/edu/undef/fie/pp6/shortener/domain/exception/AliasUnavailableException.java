package ar.edu.undef.fie.pp6.shortener.domain.exception;

/** Se agotaron los intentos de asignar un alias libre (D11). */
public class AliasUnavailableException extends RuntimeException {

	public AliasUnavailableException(int attempts) {
		super("No se pudo asignar un alias después de " + attempts + " intentos");
	}
}
