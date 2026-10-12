package ar.edu.undef.fie.pp6.shortener.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.undef.fie.pp6.shortener.config.AppProperties;
import ar.edu.undef.fie.pp6.shortener.domain.exception.AliasUnavailableException;
import ar.edu.undef.fie.pp6.shortener.domain.exception.InvalidUrlException;
import ar.edu.undef.fie.pp6.shortener.domain.model.ShortLink;
import ar.edu.undef.fie.pp6.shortener.domain.port.AliasGenerator;
import ar.edu.undef.fie.pp6.shortener.domain.port.ExpirationPolicy;
import ar.edu.undef.fie.pp6.shortener.domain.port.ShortLinkRepository;
import ar.edu.undef.fie.pp6.shortener.domain.port.UrlValidator;
import ar.edu.undef.fie.pp6.shortener.support.ScriptedAliasGenerator;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Sin @Transactional en la clase: cada creación usa transacciones reales, como en producción. */
@DisplayName("Grupo C: creación de enlaces (D6, D11-D13, I2)")
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:hsqldb:mem:servicetest",
		"app.base-url=https://sho.rt",
		"spring.jpa.show-sql=false",
		"hql-console.enabled=false" })
class ShortenLinkServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	@Autowired private UrlValidator urlValidator;
	@Autowired private AliasGenerator realGenerator;
	@Autowired private ExpirationPolicy expirationPolicy;
	@Autowired private ShortLinkRepository repository;
	@Autowired private LinkUrls linkUrls;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private AppProperties properties;
	@Autowired private EntityManager entityManager;

	private TransactionTemplate tx;

	@BeforeEach
	void cleanDatabase() {
		tx = new TransactionTemplate(transactionManager);
		tx.executeWithoutResult(s -> repository.deleteExpiredBefore(NOW.plus(Duration.ofDays(36_500))));
	}

	private ShortenLinkService service(AliasGenerator generator) {
		return service(generator, repository);
	}

	private ShortenLinkService service(AliasGenerator generator, ShortLinkRepository repo) {
		return new ShortenLinkService(urlValidator, generator, expirationPolicy, repo, linkUrls,
				transactionManager, CLOCK, properties);
	}

	private void store(String alias, String url, Instant createdAt, Instant expiresAt) {
		tx.executeWithoutResult(s -> repository.persist(new ShortLink(alias, url, createdAt, expiresAt)));
	}

	private long rowCount() {
		return entityManager.createQuery("select count(s) from ShortLink s", Long.class).getSingleResult();
	}

	@Test
	void createsAndPersistsALinkThatExpiresSixtyMinutesLater() {
		ShortLink link = service(new ScriptedAliasGenerator(null).force("abcde")).shorten("https://ejemplo.com");

		assertThat(link.getAlias()).isEqualTo("abcde");
		assertThat(link.getCreatedAt()).isEqualTo(NOW);
		assertThat(link.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
		assertThat(repository.findByAlias("abcde")).get()
				.extracting(ShortLink::getOriginalUrl).isEqualTo("https://ejemplo.com");
	}

	@Test
	void tc21_collisionWithALiveLinkRetriesAndNeverModifiesIt() {
		store("xxxxx", "https://original.com", NOW.minusSeconds(60), NOW.plusSeconds(60));

		ShortLink link = service(new ScriptedAliasGenerator(null).force("xxxxx", "yyyyy")).shorten("https://otro.com");

		assertThat(link.getAlias()).isEqualTo("yyyyy");
		assertThat(repository.findByAlias("xxxxx")).get()
				.extracting(ShortLink::getOriginalUrl).isEqualTo("https://original.com");
	}

	@Test
	void tc22_expiredLinkStillStoredIsReassigned() {
		store("xxxxx", "https://viejo.com", NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)));

		ShortLink link = service(new ScriptedAliasGenerator(null).force("xxxxx")).shorten("https://nuevo.com");

		assertThat(link.getAlias()).isEqualTo("xxxxx");
		ShortLink stored = repository.findByAlias("xxxxx").orElseThrow();
		assertThat(stored.getOriginalUrl()).isEqualTo("https://nuevo.com");
		assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
		assertThat(rowCount()).isEqualTo(1);
	}

	@Test
	void tc23_givesUpAfterTheConfiguredNumberOfAttempts() {
		store("xxxxx", "https://original.com", NOW, NOW.plusSeconds(60));
		ScriptedAliasGenerator generator = new ScriptedAliasGenerator(null);
		for (int i = 0; i < properties.alias().maxAttempts(); i++) {
			generator.force("xxxxx");
		}

		assertThatThrownBy(() -> service(generator).shorten("https://otro.com"))
				.isInstanceOf(AliasUnavailableException.class);
		assertThat(generator.calls()).isEqualTo(10);
		assertThat(rowCount()).isEqualTo(1);
	}

	@Test
	void tc24_sameUrlTwiceGetsTwoDifferentAliasesEachWithItsOwnTtl() {
		ShortenLinkService service = service(realGenerator);

		ShortLink first = service.shorten("https://misma.com");
		ShortLink second = service.shorten("https://misma.com");

		assertThat(first.getAlias()).isNotEqualTo(second.getAlias());
		assertThat(List.of(first.getExpiresAt(), second.getExpiresAt()))
				.containsOnly(NOW.plus(Duration.ofMinutes(60)));
		assertThat(rowCount()).isEqualTo(2);
	}

	@Test
	void tc25_aliasThatWouldRedirectToItselfIsDiscarded() {
		ShortLink link = service(new ScriptedAliasGenerator(null).force("aaaaa", "bbbbb"))
				.shorten("https://sho.rt/aaaaa");

		assertThat(link.getAlias()).isEqualTo("bbbbb");
		assertThat(repository.findByAlias("aaaaa")).isEmpty();
	}

	@Test
	void tc30_invalidUrlIsRejectedWithoutSideEffects() {
		ScriptedAliasGenerator generator = new ScriptedAliasGenerator(null);

		assertThatThrownBy(() -> service(generator).shorten("ftp://ejemplo.com"))
				.isInstanceOf(InvalidUrlException.class);
		assertThat(generator.calls()).isZero();
		assertThat(rowCount()).isZero();
	}

	@Test
	void tc31_concurrentCreationsOfTheSameAliasBothSucceedWithDifferentAliases() throws Exception {
		ScriptedAliasGenerator generator = new ScriptedAliasGenerator(realGenerator).force("ccccc", "ccccc");
		ShortLinkRepository bothSeeTheAliasFree = new BarrierAfterFirstReads(repository, 2);
		ShortenLinkService service = service(generator, bothSeeTheAliasFree);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<ShortLink> a = pool.submit(() -> service.shorten("https://a.com"));
			Future<ShortLink> b = pool.submit(() -> service.shorten("https://b.com"));
			ShortLink linkA = a.get(30, TimeUnit.SECONDS);
			ShortLink linkB = b.get(30, TimeUnit.SECONDS);

			assertThat(linkA.getAlias()).isNotEqualTo(linkB.getAlias());
			assertThat(List.of(linkA.getAlias(), linkB.getAlias())).contains("ccccc");
			assertThat(rowCount()).isEqualTo(2);
			assertThat(repository.findByAlias(linkA.getAlias())).get()
					.extracting(ShortLink::getOriginalUrl).isEqualTo("https://a.com");
			assertThat(repository.findByAlias(linkB.getAlias())).get()
					.extracting(ShortLink::getOriginalUrl).isEqualTo("https://b.com");
		} finally {
			pool.shutdownNow();
		}
	}

	/** Las primeras N lecturas esperan a que todas ocurran: así los dos hilos ven el alias libre antes del INSERT. */
	private static final class BarrierAfterFirstReads implements ShortLinkRepository {

		private final ShortLinkRepository delegate;
		private final CyclicBarrier barrier;
		private final AtomicInteger reads = new AtomicInteger();
		private final int parties;

		BarrierAfterFirstReads(ShortLinkRepository delegate, int parties) {
			this.delegate = delegate;
			this.parties = parties;
			this.barrier = new CyclicBarrier(parties);
		}

		@Override
		public Optional<ShortLink> findByAlias(String alias) {
			Optional<ShortLink> result = delegate.findByAlias(alias);
			if (reads.incrementAndGet() <= parties) {
				try {
					barrier.await(10, TimeUnit.SECONDS);
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}
			return result;
		}

		@Override
		public void persist(ShortLink link) {
			delegate.persist(link);
		}

		@Override
		public void deleteByAlias(String alias) {
			delegate.deleteByAlias(alias);
		}

		@Override
		public int deleteExpiredBefore(Instant now) {
			return delegate.deleteExpiredBefore(now);
		}
	}
}
