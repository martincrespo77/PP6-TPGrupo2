package ar.edu.undef.fie.pp6.shortener.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("CORS para la extensión (D45)")
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:hsqldb:mem:corstest",
		"spring.jpa.show-sql=false",
		"hql-console.enabled=false" })
@AutoConfigureMockMvc
class CorsConfigTest {

	private static final String CHROME = "chrome-extension://abcdefghijklmnopabcdefghijklmnop";
	private static final String FIREFOX = "moz-extension://2b1c7e4a-9f3d-4c1e-8a5b-6d7e8f9a0b1c";

	@Autowired private MockMvc mvc;

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { CHROME, FIREFOX })
	void preflightFromTheExtensionIsAllowed(String origin) throws Exception {
		mvc.perform(options("/api/v1/links")
						.header(HttpHeaders.ORIGIN, origin)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("content-type")))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
	}

	@Test
	void createdLinkResponseExposesLocationToTheExtension() throws Exception {
		mvc.perform(post("/api/v1/links")
						.header(HttpHeaders.ORIGIN, CHROME)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"url\":\"https://www.undef.edu.ar/\"}"))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CHROME))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Location")))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Retry-After")));
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "https://sitio-malicioso.example", "http://localhost:3000", "chrome-extension-falso.example" })
	void otherOriginsAreRejected(String origin) throws Exception {
		mvc.perform(options("/api/v1/links")
						.header(HttpHeaders.ORIGIN, origin)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void deleteIsNotAllowedEvenFromTheExtension() throws Exception {
		mvc.perform(options("/api/v1/links")
						.header(HttpHeaders.ORIGIN, CHROME)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
				.andExpect(status().isForbidden());
	}

	@Test
	void corsOnlyAppliesToTheApiNotToTheRedirect() throws Exception {
		mvc.perform(get("/zzzzz").header(HttpHeaders.ORIGIN, CHROME))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}
}
