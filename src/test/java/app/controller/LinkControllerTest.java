package app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.support.IntegrationTest;
import app.support.MutableClock;
import app.support.QrDecoder;
import app.support.ScriptedAliasGenerator;
import app.support.TestDb;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class LinkControllerTest {

	private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
	private static final Instant EXPIRES = NOW.plus(Duration.ofMinutes(60));
	private static final String DRIVE = "https://drive.google.com/drive/folders/1a2b3c4d5e6f7g8h9?usp=sharing";

	@Autowired private MockMvc mvc;
	@Autowired private ScriptedAliasGenerator generator;
	@Autowired private MutableClock clock;
	@Autowired private TestDb db;

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
	@DisplayName("API: POST /api/v1/links (§10.1)")
	class Shorten {

		private static String body(String url) {
			return "{\"url\":\"" + url + "\"}";
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

			assertThat(db.count()).isZero();
		}

		@Test
		void tc23_returns503WhenNoAliasCanBeAssigned() throws Exception {
			db.store("taken", "https://a.com", NOW, NOW.plusSeconds(60));
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

	@Nested
	@DisplayName("Grupo F: QR - GET /api/v1/links/{alias}/qr (§10.3)")
	class Qr {

		@BeforeEach
		void storeALink() {
			db.store("xt3se", DRIVE, NOW, EXPIRES);
		}

		private byte[] png(String path) throws Exception {
			return mvc.perform(get(path))
					.andExpect(status().isOk())
					.andExpect(content().contentType(MediaType.IMAGE_PNG))
					.andReturn().getResponse().getContentAsByteArray();
		}

		@Test
		void tc50_qrEncodesExactlyTheShortUrlBuiltFromBaseUrl() throws Exception {
			byte[] png = png("/api/v1/links/xt3se/qr");

			assertThat(QrDecoder.decode(png)).isEqualTo("https://sho.rt/xt3se");
			assertThat(QrDecoder.image(png).getWidth()).as("tamaño por defecto").isEqualTo(256);
		}

		@Test
		void tc50_ignoresRequestHeadersWhenBuildingTheContent() throws Exception {
			byte[] png = mvc.perform(get("/api/v1/links/xt3se/qr").header("Host", "evil.example").header("X-Forwarded-Host", "evil.example"))
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsByteArray();

			assertThat(QrDecoder.decode(png)).isEqualTo("https://sho.rt/xt3se");
		}

		@Test
		void uppercaseAliasGetsTheQrOfTheLowercaseLink() throws Exception {
			assertThat(QrDecoder.decode(png("/api/v1/links/XT3SE/qr"))).isEqualTo("https://sho.rt/xt3se");
		}

		@ParameterizedTest
		@ValueSource(ints = { 128, 512, 1024 })
		void honoursTheRequestedSizeWithinRange(int size) throws Exception {
			assertThat(QrDecoder.image(png("/api/v1/links/xt3se/qr?size=" + size)).getWidth()).isEqualTo(size);
		}

		@Test
		void tc51_downloadAddsAttachmentWithAliasFileName() throws Exception {
			mvc.perform(get("/api/v1/links/xt3se/qr").param("download", "true"))
					.andExpect(status().isOk())
					.andExpect(header().string("Content-Disposition", "attachment; filename=\"xt3se.png\""));
		}

		@Test
		void downloadOfAnUppercaseAliasIsNamedAfterTheLowercaseLink() throws Exception {
			mvc.perform(get("/api/v1/links/XT3SE/qr").param("download", "true"))
					.andExpect(status().isOk())
					.andExpect(header().string("Content-Disposition", "attachment; filename=\"xt3se.png\""));
		}

		@Test
		void withoutDownloadTheImageIsShownInline() throws Exception {
			mvc.perform(get("/api/v1/links/xt3se/qr"))
					.andExpect(status().isOk())
					.andExpect(header().doesNotExist("Content-Disposition"));
		}

		@Test
		void tc52_expiredLinkHasNoQr() throws Exception {
			clock.set(EXPIRES);

			mvc.perform(get("/api/v1/links/xt3se/qr"))
					.andExpect(status().isNotFound())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.detail").value("Este enlace expiró o no existe"));
		}

		@Test
		void tc52_nonExistentAliasHasNoQr() throws Exception {
			mvc.perform(get("/api/v1/links/nunca/qr"))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.detail").value("Este enlace expiró o no existe"));
		}

		@Test
		void errorsAreReturnedEvenWhenTheClientOnlyAcceptsPng() throws Exception {
			mvc.perform(get("/api/v1/links/nunca/qr").accept(MediaType.IMAGE_PNG))
					.andExpect(status().isNotFound());
			mvc.perform(get("/api/v1/links/xt3se/qr").param("size", "64").accept(MediaType.IMAGE_PNG))
					.andExpect(status().isBadRequest());
		}

		@ParameterizedTest
		@ValueSource(ints = { 64, 127, 1025, 2048 })
		void tc53_sizeOutOfRangeReturns400(int size) throws Exception {
			mvc.perform(get("/api/v1/links/xt3se/qr").param("size", String.valueOf(size)))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.detail").value("El tamaño del QR debe estar entre 128 y 1024 píxeles"));
		}

		@Test
		void nonNumericSizeReturns400() throws Exception {
			mvc.perform(get("/api/v1/links/xt3se/qr").param("size", "grande"))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
	}
}
