package ar.edu.undef.fie.pp6.shortener.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Lo verificable sin navegador del cliente web (§11.1). Los estados se prueban en el navegador. */
@DisplayName("Cliente web: archivos estáticos y accesibilidad mínima (§11.1)")
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:hsqldb:mem:webtest",
		"spring.jpa.show-sql=false",
		"hql-console.enabled=false" })
@AutoConfigureMockMvc
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
				.contains("'/api/v1/links'", "secondsRemaining")
				.doesNotContain("http://", "https://");
	}

	@Test
	void stylesheetIsServedAndHiddenWinsOverDisplayClasses() throws Exception {
		assertThat(fetch("/styles.css")).contains("[hidden] { display: none !important; }");
	}
}
