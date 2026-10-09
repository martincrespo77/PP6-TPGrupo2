package ar.edu.undef.fie.pp6.shortener.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.undef.fie.pp6.shortener.domain.model.ShortLink;
import ar.edu.undef.fie.pp6.shortener.domain.port.ShortLinkRepository;
import ar.edu.undef.fie.pp6.shortener.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Grupo A: resolución de un alias (I1, D7, Q7)")
class ResolveLinkServiceTest {

	private static final Instant CREATED = Instant.parse("2026-10-09T12:00:00Z");
	private static final Instant EXPIRES = CREATED.plus(Duration.ofMinutes(60));
	private static final String TARGET = "https://ejemplo.com/destino";

	private final MutableClock clock = new MutableClock(CREATED);
	private final InMemoryRepository repository = new InMemoryRepository();
	private final ResolveLinkService service = new ResolveLinkService(repository, clock);

	@BeforeEach
	void storeALink() {
		repository.persist(new ShortLink("xt3se", TARGET, CREATED, EXPIRES));
	}

	@Test
	void tc01_resolvesAtCreationInstant() {
		assertThat(service.resolve("xt3se")).contains(TARGET);
	}

	@Test
	void tc02_resolvesOneMillisecondBeforeExpiring() {
		clock.set(EXPIRES.minusMillis(1));

		assertThat(service.resolve("xt3se")).contains(TARGET);
	}

	@Test
	void tc03_doesNotResolveAtTheExactExpirationInstant() {
		clock.set(EXPIRES);

		assertThat(service.resolve("xt3se")).isEmpty();
	}

	@Test
	void tc04_doesNotResolveAnExpiredLinkEvenIfTheRowIsStillThere() {
		clock.set(CREATED.plus(Duration.ofMinutes(61)));

		assertThat(service.resolve("xt3se")).isEmpty();
		assertThat(repository.findByAlias("xt3se")).as("el cron no corrió: la fila sigue").isPresent();
	}

	@Test
	void tc05_doesNotResolveAnAliasThatNeverExisted() {
		assertThat(service.resolve("nunca")).isEmpty();
	}

	@Test
	void tc06_aliasIsCaseInsensitive() {
		assertThat(service.resolve("XT3SE")).contains(TARGET);
		assertThat(service.resolve("Xt3Se")).contains(TARGET);
	}

	private static final class InMemoryRepository implements ShortLinkRepository {

		private final Map<String, ShortLink> links = new HashMap<>();

		@Override
		public void persist(ShortLink link) {
			links.put(link.getAlias(), link);
		}

		@Override
		public Optional<ShortLink> findByAlias(String alias) {
			return Optional.ofNullable(links.get(alias));
		}

		@Override
		public void deleteByAlias(String alias) {
			links.remove(alias);
		}

		@Override
		public int deleteExpiredBefore(Instant now) {
			throw new UnsupportedOperationException();
		}
	}
}
