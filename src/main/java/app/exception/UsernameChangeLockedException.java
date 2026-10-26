package app.exception;

import java.time.Instant;

/** Mientras el nombre anterior siga reservado, el usuario solo puede volver a él. */
public class UsernameChangeLockedException extends RuntimeException {

	private final String previousUsername;
	private final Instant releasedAt;

	public UsernameChangeLockedException(String previousUsername, Instant releasedAt) {
		super("Nombre anterior reservado hasta " + releasedAt + ": " + previousUsername);
		this.previousUsername = previousUsername;
		this.releasedAt = releasedAt;
	}

	public String getPreviousUsername() {
		return previousUsername;
	}

	public Instant getReleasedAt() {
		return releasedAt;
	}
}
