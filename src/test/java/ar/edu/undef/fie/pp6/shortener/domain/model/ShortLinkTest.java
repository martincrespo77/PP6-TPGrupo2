package ar.edu.undef.fie.pp6.shortener.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ShortLinkTest {

	private static final Instant CREATED_AT = Instant.parse("2026-10-09T12:00:00Z");
	private static final Instant EXPIRES_AT = CREATED_AT.plus(Duration.ofMinutes(60));

	private final ShortLink link = new ShortLink("xt3se", "https://example.com", CREATED_AT, EXPIRES_AT);

	@Nested
	@DisplayName("isExpired: intervalo semiabierto [createdAt, expiresAt) (D3, I1)")
	class IsExpired {

		@Test
		void isNotExpiredAtCreation() {
			assertThat(link.isExpired(CREATED_AT)).isFalse();
		}

		@Test
		void isNotExpiredOneMillisecondBeforeExpiration() {
			assertThat(link.isExpired(EXPIRES_AT.minusMillis(1))).isFalse();
		}

		@Test
		void isExpiredAtExactExpirationInstant() {
			assertThat(link.isExpired(EXPIRES_AT)).isTrue();
		}

		@Test
		void isExpiredAfterExpiration() {
			assertThat(link.isExpired(EXPIRES_AT.plusSeconds(60))).isTrue();
		}
	}

	@Nested
	@DisplayName("Construcción: una entidad inválida no se puede crear")
	class Construction {

		@Test
		void keepsAllFields() {
			assertThat(link.getAlias()).isEqualTo("xt3se");
			assertThat(link.getOriginalUrl()).isEqualTo("https://example.com");
			assertThat(link.getCreatedAt()).isEqualTo(CREATED_AT);
			assertThat(link.getExpiresAt()).isEqualTo(EXPIRES_AT);
		}

		@Test
		void rejectsBlankAlias() {
			assertThatThrownBy(() -> new ShortLink(" ", "https://example.com", CREATED_AT, EXPIRES_AT))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void rejectsBlankOriginalUrl() {
			assertThatThrownBy(() -> new ShortLink("xt3se", "", CREATED_AT, EXPIRES_AT))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void rejectsNullInstants() {
			assertThatThrownBy(() -> new ShortLink("xt3se", "https://example.com", null, EXPIRES_AT))
					.isInstanceOf(NullPointerException.class);
			assertThatThrownBy(() -> new ShortLink("xt3se", "https://example.com", CREATED_AT, null))
					.isInstanceOf(NullPointerException.class);
		}

		@Test
		void rejectsExpirationNotAfterCreation() {
			assertThatThrownBy(() -> new ShortLink("xt3se", "https://example.com", CREATED_AT, CREATED_AT))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void equalityIsByAlias() {
		ShortLink sameAlias = new ShortLink("xt3se", "https://other.com", CREATED_AT, EXPIRES_AT.plusSeconds(1));
		assertThat(link).isEqualTo(sameAlias).hasSameHashCodeAs(sameAlias);
	}
}
