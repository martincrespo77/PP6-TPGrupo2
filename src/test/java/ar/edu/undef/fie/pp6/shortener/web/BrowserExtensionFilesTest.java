package ar.edu.undef.fie.pp6.shortener.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Extensión Chrome/Firefox (§11.2)")
class BrowserExtensionFilesTest {

	private static final Path DIR = Path.of("browser-extension");
	private static final Pattern API_BASE_URL = Pattern.compile("apiBaseUrl:\\s*'([^']+)'");

	private static String read(String file) throws IOException {
		return Files.readString(DIR.resolve(file), StandardCharsets.UTF_8);
	}

	private static JsonNode manifest() throws IOException {
		return JsonMapper.builder().build().readTree(read("manifest.json"));
	}

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(node -> values.add(node.asString()));
		return values;
	}

	@Test
	void manifestIsV3WithASinglePermissionAndAFirefoxId() throws Exception {
		JsonNode manifest = manifest();

		assertThat(manifest.path("manifest_version").asInt()).isEqualTo(3);
		assertThat(strings(manifest.path("permissions"))).containsExactly("activeTab");
		assertThat(manifest.path("action").path("default_popup").asString()).isEqualTo("popup.html");
		assertThat(manifest.path("browser_specific_settings").path("gecko").path("id").asString()).isNotBlank();
		assertThat(manifest.path("version").asString()).matches("\\d+\\.\\d+\\.\\d+");
	}

	@Test
	void configuredApiIsCoveredByHostPermissions() throws Exception {
		Matcher matcher = API_BASE_URL.matcher(read("config.js"));
		assertThat(matcher.find()).as("config.js define apiBaseUrl").isTrue();
		String apiBaseUrl = matcher.group(1);

		assertThat(apiBaseUrl).doesNotEndWith("/");
		assertThat(strings(manifest().path("host_permissions"))).contains(apiBaseUrl + "/*");
	}

	@Test
	void popupLoadsTheConfigBeforeTheScriptAndHasNoInlineCode() throws Exception {
		String popup = read("popup.html");

		assertThat(popup).contains("<html lang=\"es\">", "role=\"status\"", ">ACORTAR<", "href=\"popup.css\"");
		assertThat(popup.indexOf("src=\"config.js\"")).isPositive().isLessThan(popup.indexOf("src=\"popup.js\""));
		assertThat(popup).doesNotContainPattern("<script>|<script\\s+(?!src=)|\\son\\w+=\"");
		assertThat(DIR.resolve("popup.css")).exists();
	}

	@Test
	void scriptUsesTheConfiguredApiAndTheServerCountdown() throws Exception {
		assertThat(read("popup.js"))
				.contains("PP6_CONFIG.apiBaseUrl", "'/api/v1/links'", "secondsRemaining",
						"Esta página no se puede acortar (solo http/https)")
				.doesNotContain("http://", "https://");
	}
}
