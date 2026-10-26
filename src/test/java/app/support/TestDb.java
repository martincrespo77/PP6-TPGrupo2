package app.support;

import app.mappings.AppUser;
import app.mappings.PersonalLink;
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

	/** Usuario con el nombre ya elegido. */
	public AppUser storeUser(String googleSubject, String username, Instant createdAt) {
		return transaction.execute(s -> {
			AppUser user = new AppUser(googleSubject, googleSubject + "@gmail.com", "usuario-" + googleSubject, createdAt);
			user.chooseUsername(username);
			em.persist(user);
			return user;
		});
	}

	public void storePersonalLink(String username, String alias, String url, Instant createdAt, Instant expiresAt) {
		transaction.executeWithoutResult(s -> {
			AppUser owner = em.createQuery("select u from AppUser u where u.username = :username", AppUser.class)
					.setParameter("username", username)
					.getSingleResult();
			em.persist(new PersonalLink(owner, alias, url, createdAt, expiresAt));
		});
	}

	public long countPersonalLinks() {
		return transaction.execute(s -> em.createQuery("select count(p) from PersonalLink p", Long.class).getSingleResult());
	}

	public void deleteAll() {
		transaction.executeWithoutResult(s -> {
			em.createQuery("delete from ShortLink").executeUpdate();
			em.createQuery("delete from PersonalLink").executeUpdate();
			em.createQuery("delete from LoginRequest").executeUpdate();
			em.createQuery("delete from AppUser").executeUpdate();
		});
	}
}
