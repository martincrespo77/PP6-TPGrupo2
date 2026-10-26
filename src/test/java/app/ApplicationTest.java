package app;

import static org.assertj.core.api.Assertions.assertThat;

import app.service.AliasGenerator;
import app.service.LinkService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:hsqldb:mem:defaults",
		"app.base-url=http://localhost:8080/",
		"hql-console.enabled=false" })
class ApplicationTest {

	@Autowired private Environment env;
	@Autowired private Clock clock;
	@Autowired private AliasGenerator aliasGenerator;
	@Autowired private LinkService linkService;

	@Test
	void contextLoadsWithTheDefaultConfiguration() {
		assertThat(env.getProperty("app.link.ttl")).isEqualTo("60m");
		assertThat(env.getProperty("app.alias.alphabet")).hasSize(33).startsWith("0123456789").doesNotContain("o", "l", "i");
		assertThat(env.getProperty("app.alias.reserved")).contains("api", "admin", "favicon.ico");
		assertThat(env.getProperty("app.alias.max-attempts")).isEqualTo("10");
		assertThat(env.getProperty("app.cleanup.cron")).isEqualTo("0 0 3 * * *");
		assertThat(aliasGenerator.generate(Set.of())).isEqualTo("1");
	}

	@Test
	void shortUrlIgnoresTrailingSlashesInTheBaseUrl() {
		assertThat(linkService.shortUrl("abcde")).isEqualTo("http://localhost:8080/abcde");
	}

	@Test
	void clockUsesUtc() {
		assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
	}

	@Test
	void clockTicksInWholeMillisecondsSoInstantsMatchWhatTheDatabaseStores() {
		for (int i = 0; i < 1_000; i++) {
			Instant now = clock.instant();
			assertThat(now.getNano() % 1_000_000).isZero();
		}
	}
}
