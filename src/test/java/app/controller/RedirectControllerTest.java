package app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.support.IntegrationTest;
import app.support.MutableClock;
import app.support.TestBeans;
import app.support.TestDb;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("Redirección: GET /{alias} (§10.2)")
@IntegrationTest
class RedirectControllerTest {

	private static final Instant CREATED = TestBeans.START;
	private static final Instant EXPIRES = CREATED.plus(Duration.ofMinutes(60));
	private static final String TARGET = "https://drive.google.com/drive/folders/1a2b3c4d5e6f7g8h9?usp=sharing";
	private static final String NOT_AVAILABLE = "Este enlace expiró o no existe";

	@Autowired private MockMvc mvc;
	@Autowired private MutableClock clock;
	@Autowired private TestDb db;

	@BeforeEach
	void storeALink() {
		clock.set(CREATED);
		db.store("xt3se", TARGET, CREATED, EXPIRES);
	}

	@AfterEach
	void cleanUp() {
		db.deleteAll();
	}

	@Test
	void tc01_tc32_liveLinkRedirectsWith302AndNeverWith301() throws Exception {
		mvc.perform(get("/xt3se"))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", TARGET))
				.andExpect(header().string("Cache-Control", containsString("no-store")));
	}

	@Test
	void tc02_redirectsOneMillisecondBeforeExpiring() throws Exception {
		clock.set(EXPIRES.minusMillis(1));

		mvc.perform(get("/xt3se")).andExpect(status().isFound());
	}

	@Test
	void tc03_returns404PageAtTheExactExpirationInstant() throws Exception {
		clock.set(EXPIRES);

		mvc.perform(get("/xt3se"))
				.andExpect(status().isNotFound())
				.andExpect(header().doesNotExist("Location"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString(NOT_AVAILABLE)))
				.andExpect(content().string(containsString("Crear un enlace nuevo")));
	}

	@Test
	void tc04_tc05_expiredAndNonExistentAliasesGetTheSameResponse() throws Exception {
		clock.set(CREATED.plus(Duration.ofMinutes(61)));

		String expired = mvc.perform(get("/xt3se"))
				.andExpect(status().isNotFound())
				.andReturn().getResponse().getContentAsString();
		String missing = mvc.perform(get("/nunca"))
				.andExpect(status().isNotFound())
				.andReturn().getResponse().getContentAsString();

		assertThat(expired).contains(NOT_AVAILABLE).isEqualTo(missing);
	}

	@Test
	void tc06_uppercaseAliasRedirects() throws Exception {
		mvc.perform(get("/XT3SE"))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", TARGET));
	}

	@Test
	void tc36_thereIsNoEndpointThatListsLinks() throws Exception {
		mvc.perform(get("/api/v1/links"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(content().string(not(containsString("xt3se"))))
				.andExpect(content().string(not(containsString(TARGET))));
	}

	@Test
	void staticPagesAreNotTreatedAsAliases() throws Exception {
		mvc.perform(get("/index.html")).andExpect(status().isOk());
		mvc.perform(get("/")).andExpect(status().isOk());
	}
}
