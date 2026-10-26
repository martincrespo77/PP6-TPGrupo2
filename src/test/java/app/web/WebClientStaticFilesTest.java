package app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Lo verificable sin navegador del cliente web (§11.1). Los estados se prueban en el navegador. */
@DisplayName("Cliente web: archivos estáticos y accesibilidad mínima (§11.1)")
@IntegrationTest
class WebClientStaticFilesTest {

	@Autowired private MockMvc mvc;

	/** Los estáticos no declaran charset en el header; el navegador usa el meta charset UTF-8. */
	private String fetch(String path) throws Exception {
		return mvc.perform(get(path))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	@Test
	void rootServesTheWebClient() throws Exception {
		mvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("index.html"));
	}

	@Test
	void homePageHasTheFormWithAccessibleLabelAndStatusRegion() throws Exception {
		assertThat(fetch("/index.html")).contains(
				"<html lang=\"es\">",
				"<meta charset=\"UTF-8\">",
				"<label for=\"url\">Dirección a acortar</label>",
				"id=\"url\"",
				">ACORTAR<",
				"role=\"status\"",
				"src=\"app.js\"",
				"href=\"styles.css\"");
	}

	@Test
	void urlFieldDoesNotUseBrowserValidationSoTheServerMessageIsShown() throws Exception {
		assertThat(fetch("/index.html"))
				.contains("novalidate")
				.doesNotContain("type=\"url\"");
	}

	@Test
	void scriptCallsTheApiWithARelativePath() throws Exception {
		assertThat(fetch("/app.js"))
				.contains("'/api/v1/links'")
				.doesNotContain("http://", "https://");
	}

	@Test
	void resultCardIsSharedAndUsesTheServerCountdown() throws Exception {
		String index = fetch("/index.html");
		assertThat(index.indexOf("src=\"result-card.js\"")).isPositive().isLessThan(index.indexOf("src=\"app.js\""));
		assertThat(fetch("/result-card.js")).contains("secondsRemaining").doesNotContain("http://", "https://");
	}

	@Test
	void sessionPagesUseAbsolutePathsAndSendTheCsrfToken() throws Exception {
		for (String page : new String[] { "/login.html", "/cuenta.html", "/confirmar-ingreso.html" }) {
			assertThat(fetch(page)).as(page)
					.contains("<html lang=\"es\">", "role=\"status\"", "href=\"/styles.css\"", "src=\"/session.js\"");
		}
		assertThat(fetch("/session.js")).contains("XSRF-TOKEN", "X-XSRF-TOKEN");
		assertThat(fetch("/login.html")).contains("href=\"/oauth2/authorization/google\"");
	}

	@Test
	void stylesheetIsServedAndHiddenWinsOverDisplayClasses() throws Exception {
		assertThat(fetch("/styles.css")).contains("[hidden] { display: none !important; }");
	}
}
