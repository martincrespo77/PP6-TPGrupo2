package ar.edu.undef.fie.pp6.shortener.domain.exception;

public class AliasAlreadyTakenException extends RuntimeException {

	private final String alias;

	public AliasAlreadyTakenException(String alias, Throwable cause) {
		super("El alias '" + alias + "' ya está en uso", cause);
		this.alias = alias;
	}

	public String getAlias() {
		return alias;
	}
}
