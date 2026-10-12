package ar.edu.undef.fie.pp6.shortener.web.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.undef.fie.pp6.shortener.config.AppProperties;
import ar.edu.undef.fie.pp6.shortener.domain.model.ShortLink;
import ar.edu.undef.fie.pp6.shortener.domain.port.ShortLinkRepository;
import ar.edu.undef.fie.pp6.shortener.infrastructure.alias.RandomAliasGenerator;
import ar.edu.undef.fie.pp6.shortener.support.ScriptedAliasGenerator;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("API: POST /api/v1/links (§10.1)")
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:hsqldb:mem:apitest",
		"app.base-url=https://sho.rt",
		"spring.jpa.show-sql=false",
		"hql-console.enabled=false" })
@AutoConfigureMockMvc
@Import(LinkApiControllerTest.Fixtures.class)
class LinkApiControllerTest {

	private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
	private static final String DRIVE = "https://drive.google.com/drive/folders/1a2b3c4d5e6f7g8h9?usp=sharing";

	@TestConfiguration
	static class Fixtures {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}

		@Bean
		@Primary
		ScriptedAliasGenerator scriptedAliasGenerator(AppProperties properties) {
			return new ScriptedAliasGenerator(new RandomAliasGenerator(properties));
		}
	}

	@Autowired private MockMvc mvc;
	@Autowired private ScriptedAliasGenerator generator;
	@Autowired private ShortLinkRepository repository;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private EntityManager entityManager;

	@AfterEach
	void cleanUp() {
		generator.reset();
		new TransactionTemplate(transactionManager)
				.executeWithoutResult(s -> repository.deleteExpiredBefore(NOW.plus(Duration.ofDays(36_500))));
	}

	private static String body(String url) {
		return "{\"url\":\"" + url + "\"}";
	}

	private long rowCount() {
		return entityManager.createQuery("select count(s) from ShortLink s", Long.class).getSingleResult();
	}

	@Test
	void tc61_createsALinkAndReturnsExactlyTheContractFields() throws Exception {
		generator.force("xt3se");

		mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body(DRIVE)))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "https://sho.rt/xt3se"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.*", hasSize(7)))
				.andExpect(jsonPath("$.alias").value("xt3se"))
				.andExpect(jsonPath("$.shortUrl").value("https://sho.rt/xt3se"))
				.andExpect(jsonPath("$.originalUrl").value(DRIVE))
				.andExpect(jsonPath("$.createdAt").value("2026-10-08T12:00:00Z"))
				.andExpect(jsonPath("$.expiresAt").value("2026-10-08T13:00:00Z"))
				.andExpect(jsonPath("$.secondsRemaining").value(3600))
				.andExpect(jsonPath("$.qrUrl").value("https://sho.rt/api/v1/links/xt3se/qr"));
	}

	@Test
	void tc60_secondsRemainingIsTheFullTtlAtCreation() throws Exception {
		mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body("http://ejemplo.com")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.secondsRemaining").value(3600))
				.andExpect(jsonPath("$.expiresAt").value("2026-10-08T13:00:00Z"));
	}

	@Test
	void tc33_shortUrlComesFromBaseUrlAndNeverFromRequestHeaders() throws Exception {
		mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body(DRIVE))
						.header("Host", "evil.example")
						.header("X-Forwarded-Host", "evil.example")
						.header("X-Forwarded-Proto", "http"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shortUrl", startsWith("https://sho.rt/")))
				.andExpect(jsonPath("$.qrUrl", startsWith("https://sho.rt/")));
	}

	@Test
	void tc13_urlWithoutSchemeReturns400ProblemDetailWithD18Message() throws Exception {
		mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body("drive.google.com/x")))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.title").value("URL inválida"))
				.andExpect(jsonPath("$.detail").value("La dirección debe empezar con http:// o https://"));
	}

	@Test
	void tc14_missingUrlFieldReturns400() throws Exception {
		mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Ingresá la dirección que querés acortar"));
	}

	@Test
	void malformedJsonReturns400ProblemDetailInSpanish() throws Exception {
		mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content("{url:"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail", containsString("{\"url\"")));
	}

	@Test
	void tc30_invalidUrlDoesNotInsertAnyRow() throws Exception {
		mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body("ftp://ejemplo.com")))
				.andExpect(status().isBadRequest());

		Assertions.assertThat(rowCount()).isZero();
	}

	@Test
	void tc23_returns503WhenNoAliasCanBeAssigned() throws Exception {
		new TransactionTemplate(transactionManager).executeWithoutResult(s ->
				repository.persist(new ShortLink("taken", "https://a.com", NOW, NOW.plusSeconds(60))));
		for (int i = 0; i < 10; i++) {
			generator.force("taken");
		}

		mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body(DRIVE)))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(503))
				.andExpect(header().exists("Retry-After"));
	}

	@Test
	void endpointIsDocumentedInOpenApi() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/api/v1/links")));
	}
}
