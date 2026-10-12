package ar.edu.undef.fie.pp6.shortener.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.undef.fie.pp6.shortener.domain.model.ShortLink;
import ar.edu.undef.fie.pp6.shortener.domain.port.ShortLinkRepository;
import ar.edu.undef.fie.pp6.shortener.support.MutableClock;
import ar.edu.undef.fie.pp6.shortener.support.QrDecoder;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

@DisplayName("Grupo F: QR - GET /api/v1/links/{alias}/qr (§10.3)")
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:hsqldb:mem:qrtest",
		"app.base-url=https://sho.rt",
		"spring.jpa.show-sql=false",
		"hql-console.enabled=false" })
@AutoConfigureMockMvc
@Import(LinkQrControllerTest.Fixtures.class)
class LinkQrControllerTest {

	private static final Instant CREATED = Instant.parse("2026-10-09T12:00:00Z");
	private static final Instant EXPIRES = CREATED.plus(Duration.ofMinutes(60));
	private static final String TARGET = "https://drive.google.com/drive/folders/1a2b3c4d5e6f7g8h9?usp=sharing";

	@TestConfiguration
	static class Fixtures {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(CREATED);
		}
	}

	@Autowired private MockMvc mvc;
	@Autowired private MutableClock clock;
	@Autowired private ShortLinkRepository repository;
	@Autowired private PlatformTransactionManager transactionManager;

	@BeforeEach
	void storeALink() {
		clock.set(CREATED);
		new TransactionTemplate(transactionManager)
				.executeWithoutResult(s -> repository.persist(new ShortLink("xt3se", TARGET, CREATED, EXPIRES)));
	}

	@AfterEach
	void cleanUp() {
		new TransactionTemplate(transactionManager)
				.executeWithoutResult(s -> repository.deleteExpiredBefore(CREATED.plus(Duration.ofDays(36_500))));
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
