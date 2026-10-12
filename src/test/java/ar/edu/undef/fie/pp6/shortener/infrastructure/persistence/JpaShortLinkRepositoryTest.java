package ar.edu.undef.fie.pp6.shortener.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.undef.fie.pp6.shortener.domain.exception.AliasAlreadyTakenException;
import ar.edu.undef.fie.pp6.shortener.domain.model.ShortLink;
import ar.edu.undef.fie.pp6.shortener.domain.port.ShortLinkRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = "hql-console.enabled=false")
@Import(JpaShortLinkRepository.class)
class JpaShortLinkRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

	@Autowired
	private ShortLinkRepository repository;

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private static ShortLink link(String alias, String url, Instant createdAt) {
		return new ShortLink(alias, url, createdAt, createdAt.plus(Duration.ofMinutes(60)));
	}

	private static ShortLink linkExpiringAt(String alias, Instant expiresAt) {
		return new ShortLink(alias, "https://example.com/" + alias, expiresAt.minus(Duration.ofMinutes(60)), expiresAt);
	}

	@Test
	void persistsAndFindsByAlias() {
		repository.persist(link("abcde", "https://example.com", NOW));
		entityManager.clear();

		ShortLink found = repository.findByAlias("abcde").orElseThrow();

		assertThat(found.getOriginalUrl()).isEqualTo("https://example.com");
		assertThat(found.getCreatedAt()).isEqualTo(NOW);
		assertThat(found.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
	}

	@Test
	void findByAliasReturnsEmptyWhenMissing() {
		assertThat(repository.findByAlias("nopes")).isEmpty();
	}

	@Test
	void deleteByAliasRemovesTheLink() {
		repository.persist(link("abcde", "https://example.com", NOW));
		entityManager.clear();

		repository.deleteByAlias("abcde");
		entityManager.clear();

		assertThat(repository.findByAlias("abcde")).isEmpty();
	}

	@Test
	void deleteByAliasIgnoresMissingAlias() {
		repository.deleteByAlias("nopes");

		assertThat(repository.findByAlias("nopes")).isEmpty();
	}

	@Test
	void expiredAliasCanBeDeletedAndReassignedInTheSameTransaction() {
		repository.persist(link("abcde", "https://old.example.com", NOW.minus(Duration.ofHours(2))));
		ShortLink expired = repository.findByAlias("abcde").orElseThrow();
		assertThat(expired.isExpired(NOW)).isTrue();

		repository.deleteByAlias("abcde");
		repository.persist(link("abcde", "https://new.example.com", NOW));
		entityManager.clear();

		assertThat(repository.findByAlias("abcde").orElseThrow().getOriginalUrl())
				.isEqualTo("https://new.example.com");
	}

	@Test
	void deleteExpiredBeforeRemovesOnlyLinksExpiredAtOrBeforeNow() {
		repository.persist(linkExpiringAt("past1", NOW.minusSeconds(1)));
		repository.persist(linkExpiringAt("exact", NOW));
		repository.persist(linkExpiringAt("futur", NOW.plusSeconds(1)));
		entityManager.clear();

		int deleted = repository.deleteExpiredBefore(NOW);

		assertThat(deleted).isEqualTo(2);
		assertThat(repository.findByAlias("past1")).isEmpty();
		assertThat(repository.findByAlias("exact")).isEmpty();
		assertThat(repository.findByAlias("futur")).isPresent();
	}

	@Test
	void writesRequireAnExistingTransaction() {
		TransactionTemplate withoutTransaction = new TransactionTemplate(transactionManager);
		withoutTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_NOT_SUPPORTED);

		assertThatThrownBy(() -> withoutTransaction.executeWithoutResult(
				status -> repository.persist(link("abcde", "https://example.com", NOW))))
				.isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void persistNeverOverwritesAnExistingAlias() {
		// Cada operación corre en su propia transacción real, como dos pedidos simultáneos.
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		tx.executeWithoutResult(status -> repository.persist(link("dupli", "https://first.example.com", NOW)));

		assertThatThrownBy(() -> tx.executeWithoutResult(
				status -> repository.persist(link("dupli", "https://second.example.com", NOW))))
				.isInstanceOf(AliasAlreadyTakenException.class);

		String stored = tx.execute(status -> repository.findByAlias("dupli").orElseThrow().getOriginalUrl());
		assertThat(stored).isEqualTo("https://first.example.com");
	}

	@AfterEach
	void cleanCommittedRows() {
		new TransactionTemplate(transactionManager)
				.executeWithoutResult(status -> repository.deleteByAlias("dupli"));
	}
}
