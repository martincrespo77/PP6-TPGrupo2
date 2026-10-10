package ar.edu.undef.fie.pp6.shortener.infrastructure.persistence;

import ar.edu.undef.fie.pp6.shortener.domain.exception.AliasAlreadyTakenException;
import ar.edu.undef.fie.pp6.shortener.domain.model.ShortLink;
import ar.edu.undef.fie.pp6.shortener.domain.port.ShortLinkRepository;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaShortLinkRepository implements ShortLinkRepository {

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void persist(ShortLink link) {
		try {
			entityManager.persist(link);
			// Sin flush, un alias duplicado recién fallaría al commit, lejos del caso de uso que debe reintentar.
			entityManager.flush();
		} catch (EntityExistsException | ConstraintViolationException e) {
			// La única restricción que puede violar un ShortLink ya validado es la clave primaria.
			throw new AliasAlreadyTakenException(link.getAlias(), e);
		}
	}

	@Override
	public Optional<ShortLink> findByAlias(String alias) {
		return Optional.ofNullable(entityManager.find(ShortLink.class, alias));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void deleteByAlias(String alias) {
		ShortLink link = entityManager.find(ShortLink.class, alias);
		if (link != null) {
			entityManager.remove(link);
			// Sin flush el DELETE queda pendiente en el contexto de persistencia y se pierde si alguien lo limpia.
			entityManager.flush();
		}
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public int deleteExpiredBefore(Instant now) {
		return entityManager.createQuery("delete from ShortLink s where s.expiresAt <= :now")
				.setParameter("now", now)
				.executeUpdate();
	}
}
