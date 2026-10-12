package ar.edu.undef.fie.pp6.shortener.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LinkUrlsTest {

	@Test
	void buildsShortAndQrUrlsFromTheBaseUrl() {
		LinkUrls urls = new LinkUrls("https://paradigmas6.agustingimenez.ar");

		assertThat(urls.shortUrl("xt3se")).isEqualTo("https://paradigmas6.agustingimenez.ar/xt3se");
		assertThat(urls.qrUrl("xt3se")).isEqualTo("https://paradigmas6.agustingimenez.ar/api/v1/links/xt3se/qr");
	}

	@Test
	void ignoresTrailingSlashesInTheBaseUrl() {
		assertThat(new LinkUrls("http://localhost:8080/").shortUrl("abcde")).isEqualTo("http://localhost:8080/abcde");
	}
}
