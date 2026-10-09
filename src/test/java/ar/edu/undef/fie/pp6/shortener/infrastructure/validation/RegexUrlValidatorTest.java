package ar.edu.undef.fie.pp6.shortener.infrastructure.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.undef.fie.pp6.shortener.domain.exception.InvalidUrlException;
import ar.edu.undef.fie.pp6.shortener.domain.port.UrlValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Grupo B: validación de la URL destino (D16-D19, C1)")
class RegexUrlValidatorTest {

	private static final String BASE = "https://example.com/";

	private final UrlValidator validator = new RegexUrlValidator();

	private static String urlOfLength(int length) {
		return BASE + "a".repeat(length - BASE.length());
	}

	private void assertAccepted(String url) {
		assertThatCode(() -> validator.validate(url)).doesNotThrowAnyException();
	}

	private void assertRejected(String url, String expectedMessage) {
		assertThatThrownBy(() -> validator.validate(url))
				.isInstanceOf(InvalidUrlException.class)
				.hasMessage(expectedMessage);
	}

	@Nested
	@DisplayName("Clases válidas")
	class Valid {

		@Test
		void tc10_acceptsTypicalHttpsUrl() {
			assertAccepted("https://drive.google.com/drive/folders/1a2b3c4d5e6f7g8h9?usp=sharing");
		}

		@Test
		void tc11_acceptsHttpUrl() {
			assertAccepted("http://ejemplo.com");
		}

		@Test
		void tc15_acceptsMaximumLength() {
			assertAccepted(urlOfLength(2048));
		}

		@Test
		void tc17_acceptsUrlOfTheShortenerItself() {
			assertAccepted("http://localhost:8080/abcde");
			assertAccepted("https://paradigmas6.agustingimenez.ar/abcde");
		}

		@ParameterizedTest
		@ValueSource(strings = { "http://localhost:3000", "http://192.168.1.10/x", "http://10.0.0.1:8080/a?b=c", "http://[::1]:8080/" })
		void tc19_acceptsLocalhostAndPrivateAddresses(String url) {
			assertAccepted(url);
		}

		@Test
		void acceptsUppercaseSchemeUserInfoPortQueryAndFragment() {
			assertAccepted("HTTPS://user:pass@example.com:8443/path/to?q=1&r=2#section");
		}
	}

	@Nested
	@DisplayName("Clases inválidas")
	class Invalid {

		@Test
		void tc12_rejectsUnsupportedScheme() {
			assertRejected("ftp://ejemplo.com", RegexUrlValidator.MISSING_SCHEME);
		}

		@ParameterizedTest
		@ValueSource(strings = { "drive.google.com/x", "hola mundo", "www.ejemplo.com" })
		void tc13_rejectsUrlWithoutSchemeWithD18Message(String url) {
			assertRejected(url, "La dirección debe empezar con http:// o https://");
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = { "   " })
		void tc14_rejectsEmptyOrNull(String url) {
			assertRejected(url, RegexUrlValidator.EMPTY);
		}

		@Test
		void tc16_rejectsMaximumLengthPlusOne() {
			assertRejected(urlOfLength(2049), RegexUrlValidator.TOO_LONG);
		}

		@ParameterizedTest
		@ValueSource(strings = { "https://", "http:///path", "https://?q=1" })
		void tc18_rejectsSchemeWithoutHost(String url) {
			assertRejected(url, RegexUrlValidator.INVALID_FORMAT);
		}

		@ParameterizedTest
		@ValueSource(strings = { "https://exa mple.com", "https://example.com/a b", "https://example.com:99999/" })
		void rejectsMalformedUrls(String url) {
			assertRejected(url, RegexUrlValidator.INVALID_FORMAT);
		}
	}
}
