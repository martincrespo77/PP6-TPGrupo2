package app.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.Application;
import app.dto.ShortLinkResponse;
import app.mappings.ShortLink;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class PersistenceAcrossRestartTest {

	@TempDir
	Path dataDir;

	@Test
	void tc35_linksSurviveAnApplicationRestart() {
		ShortLinkResponse created;
		try (ConfigurableApplicationContext first = start()) {
			created = first.getBean(LinkService.class).shorten("https://example.com");
		}

		try (ConfigurableApplicationContext second = start()) {
			assertThat(second.getBean(LinkService.class).findLive(created.alias()))
					.get()
					.extracting(ShortLink::getOriginalUrl, ShortLink::getExpiresAt)
					.containsExactly("https://example.com", created.expiresAt());
		}
	}

	private ConfigurableApplicationContext start() {
		String url = "jdbc:hsqldb:file:" + dataDir.resolve("shortener").toAbsolutePath() + ";shutdown=true";
		// Argumentos de línea de comandos: a diferencia de .properties(...), pisan application.properties.
		return new SpringApplicationBuilder(Application.class)
				.web(WebApplicationType.NONE)
				.run("--spring.datasource.url=" + url,
						"--spring.jpa.show-sql=false",
						"--hql-console.enabled=false");
	}
}
