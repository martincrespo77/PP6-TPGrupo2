package app.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.support.IntegrationTest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
				.contains("'/api/v1/links'", "secondsRemaining")
				.doesNotContain("http://", "https://");
	}

	@Test
	void stylesheetIsServedAndHiddenWinsOverDisplayClasses() throws Exception {
		assertThat(fetch("/styles.css")).contains("[hidden] { display: none !important; }");
	}

	@Test
	void homePageLinksToTheExtensionDownload() throws Exception {
		assertThat(fetch("/index.html")).contains("href=\"descargas/acortador-pp6.zip\"");
	}

	@Test
	void extensionIsDownloadableAsAZipThatCallsTheServerItCameFrom() throws Exception {
		byte[] zip = mvc.perform(get("/descargas/acortador-pp6.zip"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("acortador-pp6.zip")))
				.andReturn().getResponse().getContentAsByteArray();

		List<String> entries = new ArrayList<>();
		String config = null;
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
			for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
				entries.add(entry.getName());
				if (entry.getName().equals("config.js")) {
					config = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				}
			}
		}
		assertThat(entries)
				.contains("manifest.json", "config.js", "popup.html", "popup.js", "popup.css")
				.noneMatch(name -> name.contains("\\"));
		assertThat(config).contains("apiBaseUrl: 'https://sho.rt'");
	}
}
