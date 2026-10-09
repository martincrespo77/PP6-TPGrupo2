package ar.edu.undef.fie.pp6.shortener.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.undef.fie.pp6.shortener.ShortenerApplication;
import ar.edu.undef.fie.pp6.shortener.domain.model.ShortLink;
import ar.edu.undef.fie.pp6.shortener.domain.port.ShortLinkRepository;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ShortLinkPersistenceAcrossRestartTest {

	private static final Instant CREATED_AT = Instant.parse("2026-10-09T12:00:00Z");

	@TempDir
	Path dataDir;

	@Test
	void tc35_linksSurviveAnApplicationRestart() {
		ShortLink link = new ShortLink("xt3se", "https://example.com", CREATED_AT, CREATED_AT.plus(Duration.ofMinutes(60)));

		try (ConfigurableApplicationContext first = start()) {
			inTransaction(first, repository -> repository.persist(link));
		}

		try (ConfigurableApplicationContext second = start()) {
			ShortLinkRepository repository = second.getBean(ShortLinkRepository.class);
			assertThat(repository.findByAlias("xt3se"))
					.get()
					.extracting(ShortLink::getOriginalUrl, ShortLink::getExpiresAt)
					.containsExactly("https://example.com", link.getExpiresAt());
		}
	}

	private ConfigurableApplicationContext start() {
		String url = "jdbc:hsqldb:file:" + dataDir.resolve("shortener").toAbsolutePath() + ";shutdown=true";
		// Argumentos de línea de comandos: a diferencia de .properties(...), pisan application.properties.
		return new SpringApplicationBuilder(ShortenerApplication.class)
				.web(WebApplicationType.NONE)
				.run("--spring.datasource.url=" + url,
						"--spring.jpa.show-sql=false",
						"--hql-console.enabled=false");
	}

	private static void inTransaction(ConfigurableApplicationContext context,
			java.util.function.Consumer<ShortLinkRepository> work) {
		ShortLinkRepository repository = context.getBean(ShortLinkRepository.class);
		new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
				.executeWithoutResult(status -> work.accept(repository));
	}
}
