package app.support;

import app.mappings.ShortLink;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.springframework.transaction.support.TransactionTemplate;

/** Acceso directo a la tabla para preparar y verificar datos, cada operación en su transacción. */
public class TestDb {

	private final EntityManager em;
	private final TransactionTemplate transaction;

	TestDb(EntityManager em, TransactionTemplate transaction) {
		this.em = em;
		this.transaction = transaction;
	}

	public void store(String alias, String url, Instant createdAt, Instant expiresAt) {
		transaction.executeWithoutResult(s -> em.persist(new ShortLink(alias, url, createdAt, expiresAt)));
	}

	/** @return el enlace guardado, o null si no existe */
	public ShortLink find(String alias) {
		return transaction.execute(s -> em.find(ShortLink.class, alias));
	}

	public long count() {
		return transaction.execute(s -> em.createQuery("select count(s) from ShortLink s", Long.class).getSingleResult());
	}

	public void deleteAll() {
		transaction.executeWithoutResult(s -> em.createQuery("delete from ShortLink").executeUpdate());
	}
}
