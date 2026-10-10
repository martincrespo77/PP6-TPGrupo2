package ar.edu.undef.fie.pp6.shortener.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.undef.fie.pp6.shortener.domain.model.ShortLink;
import ar.edu.undef.fie.pp6.shortener.domain.port.ShortLinkRepository;
import ar.edu.undef.fie.pp6.shortener.infrastructure.scheduling.ExpiredLinksCleanupJob;
import ar.edu.undef.fie.pp6.shortener.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("Grupo E: borrado programado de vencidos (D5, I6)")
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:hsqldb:mem:cleanuptest",
		"spring.jpa.show-sql=false",
		"hql-console.enabled=false" })
@Import(ExpiredLinksCleanupServiceTest.Fixtures.class)
class ExpiredLinksCleanupServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-10T03:00:00Z");

	@TestConfiguration
	static class Fixtures {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(NOW);
		}
	}

	@Autowired private ExpiredLinksCleanupService cleanupService;
	@Autowired private ExpiredLinksCleanupJob cleanupJob;
	@Autowired private ShortLinkRepository repository;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private ScheduledTaskHolder scheduledTasks;
	@Autowired private MutableClock clock;

	private TransactionTemplate tx;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		tx = new TransactionTemplate(transactionManager);
	}

	@AfterEach
	void cleanUp() {
		tx.executeWithoutResult(s -> repository.deleteExpiredBefore(NOW.plus(Duration.ofDays(36_500))));
	}

	private void storeExpiringAt(String alias, Instant expiresAt) {
		Instant createdAt = expiresAt.minus(Duration.ofMinutes(60));
		tx.executeWithoutResult(s -> repository.persist(new ShortLink(alias, "https://ejemplo.com", createdAt, expiresAt)));
	}

	@Test
	void tc40_deletesExpiredLinksAndReturnsHowMany() {
		storeExpiringAt("venc1", NOW.minus(Duration.ofHours(5)));
		storeExpiringAt("venc2", NOW.minusSeconds(1));

		assertThat(cleanupService.purgeExpired()).isEqualTo(2);
		assertThat(repository.findByAlias("venc1")).isEmpty();
		assertThat(repository.findByAlias("venc2")).isEmpty();
	}

	@Test
	void tc41_doesNotTouchLiveLinks() {
		storeExpiringAt("vivo1", NOW.plusSeconds(1));
		storeExpiringAt("vivo2", NOW.plus(Duration.ofMinutes(59)));

		assertThat(cleanupService.purgeExpired()).isZero();
		assertThat(repository.findByAlias("vivo1")).isPresent();
		assertThat(repository.findByAlias("vivo2")).isPresent();
	}

	@Test
	void tc42_linkExpiringNowIsDeletedButOneExpiringOneMillisecondLaterIsNot() {
		storeExpiringAt("ahora", NOW);
		storeExpiringAt("unmse", NOW.plusMillis(1));

		assertThat(cleanupService.purgeExpired()).isEqualTo(1);
		assertThat(repository.findByAlias("ahora")).isEmpty();
		assertThat(repository.findByAlias("unmse")).isPresent();
	}

	@Test
	void usesTheClockToDecideWhatIsExpired() {
		storeExpiringAt("luego", NOW.plus(Duration.ofMinutes(30)));

		assertThat(cleanupService.purgeExpired()).isZero();
		clock.advance(Duration.ofMinutes(30));
		assertThat(cleanupService.purgeExpired()).isEqualTo(1);
	}

	@Test
	void jobRunsTheCleanup() {
		storeExpiringAt("venc1", NOW.minusSeconds(1));

		cleanupJob.run();

		assertThat(repository.findByAlias("venc1")).isEmpty();
	}

	@Test
	void jobIsScheduledWithTheConfiguredCron() {
		assertThat(scheduledTasks.getScheduledTasks())
				.extracting(ScheduledTask::getTask)
				.filteredOn(CronTask.class::isInstance)
				.map(task -> ((CronTask) task).getExpression())
				.contains("0 0 3 * * *");
	}
}
