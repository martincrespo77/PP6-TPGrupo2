package ar.edu.undef.fie.pp6.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.undef.fie.pp6.shortener.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@SpringBootTest(properties = "spring.datasource.url=jdbc:hsqldb:mem:testdb")
class ShortenerApplicationTests {

	@Autowired
	private AppProperties appProperties;

	@Autowired
	private Clock clock;

	@Test
	void contextLoadsWithDefaultConfiguration() {
		assertThat(appProperties.baseUrl()).isEqualTo("http://localhost:8080");
		assertThat(appProperties.link().ttl()).isEqualTo(Duration.ofMinutes(60));
		assertThat(appProperties.alias().length()).isEqualTo(5);
		assertThat(appProperties.alias().alphabet()).hasSize(31).doesNotContain("0", "o", "1", "l", "i");
		assertThat(appProperties.alias().reserved()).contains("api", "admin", "favicon.ico");
		assertThat(appProperties.alias().maxAttempts()).isEqualTo(10);
		assertThat(appProperties.cleanup().cron()).isEqualTo("0 0 3 * * *");
		assertThat(clock.getZone().getId()).isEqualTo("Z");
	}

	@Test
	void failsToStartWithoutBaseUrl() {
		new ApplicationContextRunner()
				.withUserConfiguration(PropertiesOnly.class)
				.withPropertyValues(
						"app.base-url=",
						"app.link.ttl=60m",
						"app.alias.length=5",
						"app.alias.alphabet=abc",
						"app.alias.reserved=api",
						"app.alias.max-attempts=10",
						"app.cleanup.cron=0 0 3 * * *")
				.run(context -> assertThat(context).hasFailed());
	}

	@EnableConfigurationProperties(AppProperties.class)
	static class PropertiesOnly {
	}
}
