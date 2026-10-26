package app.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Con servidor HTTP real: el csrf() de spring-security-test reemplaza el repositorio de tokens del
 * contexto compartido por MockMvc, así que la cookie solo se puede verificar en un contexto propio.
 */
@DisplayName("Las páginas reciben la cookie XSRF-TOKEN y la devuelven en X-XSRF-TOKEN")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.datasource.url=jdbc:hsqldb:mem:csrfdb",
		"spring.jpa.show-sql=false",
		"hql-console.enabled=false" })
class CsrfCookieTest {

	@Value("${local.server.port}")
	private int port;

	private final CookieManager cookies = new CookieManager();
	private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

	private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
	}

	@Test
	void pageScriptCanSendTheTokenFromTheCookie() throws Exception {
		HttpResponse<String> page = send(request("/login.html"));
		assertThat(page.statusCode()).isEqualTo(200);
		HttpCookie token = cookies.getCookieStore().getCookies().stream()
				.filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
				.findFirst()
				.orElseThrow();
		assertThat(token.isHttpOnly()).as("el JS tiene que poder leerla").isFalse();

		HttpRequest.Builder create = request("/api/v1/login-requests").POST(HttpRequest.BodyPublishers.noBody());
		assertThat(send(create).statusCode()).isEqualTo(403);
		assertThat(send(create.header("X-XSRF-TOKEN", token.getValue())).statusCode()).isEqualTo(201);
	}

	@Test
	void shorteningStillWorksWithoutTokenForTheExtension() throws Exception {
		HttpResponse<String> response = send(request("/api/v1/links")
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{\"url\":\"https://example.com\"}")));
		assertThat(response.statusCode()).isEqualTo(201);
	}
}
