package app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.dto.ShortLinkResponse;
import app.exception.AliasUnavailableException;
import app.exception.InvalidUrlException;
import app.mappings.ShortLink;
import app.support.IntegrationTest;
import app.support.MutableClock;
import app.support.ScriptedAliasGenerator;
import app.support.TestBeans;
import app.support.TestDb;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/** Sin @Transactional en la clase: cada operación usa transacciones reales, como en producción. */
@IntegrationTest
class LinkServiceTest {

	private static final Instant NOW = TestBeans.START;
	private static final Instant EXPIRES = NOW.plus(Duration.ofMinutes(60));
	private static final String TARGET = "https://ejemplo.com/destino";

	@Autowired private LinkService service;
	@Autowired private ScriptedAliasGenerator generator;
	@Autowired private MutableClock clock;
	@Autowired private TestDb db;
	@Autowired private AutowireCapableBeanFactory beanFactory;
	@Autowired private ScheduledTaskHolder scheduledTasks;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		generator.reset();
	}

	@AfterEach
	void cleanUp() {
		db.deleteAll();
	}

	@Nested
	@DisplayName("Grupo C: creación de enlaces (D6, D11-D13, I2)")
	class Shorten {

		@Test
		void createsAndPersistsALinkThatExpiresSixtyMinutesLater() {
			generator.force("abcde");

			ShortLinkResponse link = service.shorten("https://ejemplo.com");

			assertThat(link.alias()).isEqualTo("abcde");
			assertThat(link.shortUrl()).isEqualTo("https://sho.rt/abcde");
			assertThat(link.qrUrl()).isEqualTo("https://sho.rt/api/v1/links/abcde/qr");
			assertThat(link.createdAt()).isEqualTo(NOW);
			assertThat(link.expiresAt()).isEqualTo(EXPIRES);
			assertThat(link.secondsRemaining()).isEqualTo(3600);
			assertThat(db.find("abcde").getOriginalUrl()).isEqualTo("https://ejemplo.com");
		}

		@Test
		void tc21_collisionWithALiveLinkRetriesAndNeverModifiesIt() {
			db.store("xxxxx", "https://original.com", NOW.minusSeconds(60), NOW.plusSeconds(60));
			generator.force("xxxxx", "yyyyy");

			ShortLinkResponse link = service.shorten("https://otro.com");

			assertThat(link.alias()).isEqualTo("yyyyy");
			assertThat(db.find("xxxxx").getOriginalUrl()).isEqualTo("https://original.com");
		}

		@Test
		void tc22_expiredLinkStillStoredIsReassigned() {
			db.store("xxxxx", "https://viejo.com", NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)));
			generator.force("xxxxx");

			ShortLinkResponse link = service.shorten("https://nuevo.com");

			assertThat(link.alias()).isEqualTo("xxxxx");
			ShortLink stored = db.find("xxxxx");
			assertThat(stored.getOriginalUrl()).isEqualTo("https://nuevo.com");
			assertThat(stored.getExpiresAt()).isEqualTo(EXPIRES);
			assertThat(db.count()).isEqualTo(1);
		}

		@Test
		void tc23_givesUpAfterTheConfiguredNumberOfAttempts() {
			db.store("xxxxx", "https://original.com", NOW, NOW.plusSeconds(60));
			for (int i = 0; i < 10; i++) {
				generator.force("xxxxx");
			}

			assertThatThrownBy(() -> service.shorten("https://otro.com"))
					.isInstanceOf(AliasUnavailableException.class);
			assertThat(generator.calls()).isEqualTo(10);
			assertThat(db.count()).isEqualTo(1);
		}

		@Test
		void tc24_sameUrlTwiceGetsTwoDifferentAliasesEachWithItsOwnTtl() {
			ShortLinkResponse first = service.shorten("https://misma.com");
			ShortLinkResponse second = service.shorten("https://misma.com");

			assertThat(first.alias()).isNotEqualTo(second.alias());
			assertThat(List.of(first.expiresAt(), second.expiresAt())).containsOnly(EXPIRES);
			assertThat(db.count()).isEqualTo(2);
		}

		@Test
		void tc25_aliasThatWouldRedirectToItselfIsDiscarded() {
			generator.force("aaaaa", "bbbbb");

			ShortLinkResponse link = service.shorten("https://sho.rt/aaaaa");

			assertThat(link.alias()).isEqualTo("bbbbb");
			assertThat(db.find("aaaaa")).isNull();
		}

		@Test
		void tc30_invalidUrlIsRejectedWithoutSideEffects() {
			assertThatThrownBy(() -> service.shorten("ftp://ejemplo.com"))
					.isInstanceOf(InvalidUrlException.class);
			assertThat(generator.calls()).isZero();
			assertThat(db.count()).isZero();
		}

		@Test
		void neverOverwritesAnExistingAliasEvenIfItLookedFree() {
			db.store("dupli", "https://first.example.com", NOW, EXPIRES);
			generator.force("dupli", "otro1");
			// La primera lectura no ve la fila, como si otro pedido la hubiera insertado justo después.
			LinkService missesTheFirstRead = new LinkService() {
				private boolean first = true;

				@Override
				ShortLink find(String alias) {
					if (first) {
						first = false;
						return null;
					}
					return super.find(alias);
				}
			};
			beanFactory.autowireBean(missesTheFirstRead);

			ShortLinkResponse link = missesTheFirstRead.shorten("https://second.example.com");

			assertThat(link.alias()).isEqualTo("otro1");
			assertThat(db.find("dupli").getOriginalUrl()).isEqualTo("https://first.example.com");
		}

		@Test
		void tc31_concurrentCreationsOfTheSameAliasBothSucceedWithDifferentAliases() throws Exception {
			generator.force("ccccc", "ccccc");
			LinkService bothSeeTheAliasFree = new BarrierAfterFirstReads(2);
			beanFactory.autowireBean(bothSeeTheAliasFree);

			ExecutorService pool = Executors.newFixedThreadPool(2);
			try {
				Future<ShortLinkResponse> a = pool.submit(() -> bothSeeTheAliasFree.shorten("https://a.com"));
				Future<ShortLinkResponse> b = pool.submit(() -> bothSeeTheAliasFree.shorten("https://b.com"));
				ShortLinkResponse linkA = a.get(30, TimeUnit.SECONDS);
				ShortLinkResponse linkB = b.get(30, TimeUnit.SECONDS);

				assertThat(linkA.alias()).isNotEqualTo(linkB.alias());
				assertThat(List.of(linkA.alias(), linkB.alias())).contains("ccccc");
				assertThat(db.count()).isEqualTo(2);
				assertThat(db.find(linkA.alias()).getOriginalUrl()).isEqualTo("https://a.com");
				assertThat(db.find(linkB.alias()).getOriginalUrl()).isEqualTo("https://b.com");
			} finally {
				pool.shutdownNow();
			}
		}
	}

	@Nested
	@DisplayName("Grupo A: resolución de un alias (I1, D7, Q7)")
	class Resolve {

		@BeforeEach
		void storeALink() {
			db.store("xt3se", TARGET, NOW, EXPIRES);
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
			clock.set(NOW.plus(Duration.ofMinutes(61)));

			assertThat(service.resolve("xt3se")).isEmpty();
			assertThat(db.find("xt3se")).as("la limpieza no corrió: la fila sigue").isNotNull();
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
	}

	@Nested
	@DisplayName("Grupo E: borrado programado de vencidos (D5, I6)")
	class PurgeExpired {

		private void storeExpiringAt(String alias, Instant expiresAt) {
			db.store(alias, "https://ejemplo.com", expiresAt.minus(Duration.ofMinutes(60)), expiresAt);
		}

		@Test
		void tc40_deletesExpiredLinksAndReturnsHowMany() {
			storeExpiringAt("venc1", NOW.minus(Duration.ofHours(5)));
			storeExpiringAt("venc2", NOW.minusSeconds(1));

			assertThat(service.purgeExpired()).isEqualTo(2);
			assertThat(db.count()).isZero();
		}

		@Test
		void tc41_doesNotTouchLiveLinks() {
			storeExpiringAt("vivo1", NOW.plusSeconds(1));
			storeExpiringAt("vivo2", NOW.plus(Duration.ofMinutes(59)));

			assertThat(service.purgeExpired()).isZero();
			assertThat(db.find("vivo1")).isNotNull();
			assertThat(db.find("vivo2")).isNotNull();
		}

		@Test
		void tc42_linkExpiringNowIsDeletedButOneExpiringOneMillisecondLaterIsNot() {
			storeExpiringAt("ahora", NOW);
			storeExpiringAt("unmse", NOW.plusMillis(1));

			assertThat(service.purgeExpired()).isEqualTo(1);
			assertThat(db.find("ahora")).isNull();
			assertThat(db.find("unmse")).isNotNull();
		}

		@Test
		void usesTheClockToDecideWhatIsExpired() {
			storeExpiringAt("luego", NOW.plus(Duration.ofMinutes(30)));

			assertThat(service.purgeExpired()).isZero();
			clock.advance(Duration.ofMinutes(30));
			assertThat(service.purgeExpired()).isEqualTo(1);
		}

		@Test
		void isScheduledWithTheConfiguredCron() {
			assertThat(scheduledTasks.getScheduledTasks())
					.extracting(ScheduledTask::getTask)
					.filteredOn(CronTask.class::isInstance)
					.map(task -> ((CronTask) task).getExpression())
					.contains("0 0 3 * * *");
		}
	}

	/** Las primeras N lecturas esperan a que todas ocurran: así los dos hilos ven el alias libre antes del INSERT. */
	private static final class BarrierAfterFirstReads extends LinkService {

		private final CyclicBarrier barrier;
		private final AtomicInteger reads = new AtomicInteger();
		private final int parties;

		BarrierAfterFirstReads(int parties) {
			this.parties = parties;
			this.barrier = new CyclicBarrier(parties);
		}

		@Override
		ShortLink find(String alias) {
			ShortLink result = super.find(alias);
			if (reads.incrementAndGet() <= parties) {
				try {
					barrier.await(10, TimeUnit.SECONDS);
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}
			return result;
		}
	}
}
