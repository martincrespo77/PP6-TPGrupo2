package ar.edu.undef.fie.pp6.shortener.domain.port;

import ar.edu.undef.fie.pp6.shortener.domain.exception.AliasAlreadyTakenException;
import ar.edu.undef.fie.pp6.shortener.domain.model.ShortLink;
import java.time.Instant;
import java.util.Optional;

/**
 * Puerto de persistencia de enlaces. Los métodos de escritura exigen una transacción abierta por el
 * caso de uso que los llama.
 */
public interface ShortLinkRepository {

	/**
	 * Inserta un enlace nuevo. Nunca sobrescribe: si el alias ya existe lanza
	 * {@link AliasAlreadyTakenException} (I2). Tras esa excepción la transacción queda inutilizable:
	 * el reintento con otro alias debe correr en una transacción nueva.
	 */
	void persist(ShortLink link);

	Optional<ShortLink> findByAlias(String alias);

	/** Borra el enlace si existe. Deja el alias libre para reasignarlo en la misma transacción (D6). */
	void deleteByAlias(String alias);

	/** Borra los enlaces con {@code expiresAt <= now} y devuelve cuántos borró (D5, I6). */
	int deleteExpiredBefore(Instant now);
}
