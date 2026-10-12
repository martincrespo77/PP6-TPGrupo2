package app.dto;

import static org.assertj.core.api.Assertions.assertThat;

import app.mappings.ShortLink;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** secondsRemaining > 0 si y solo si el enlace sigue vigente (D24, I1). */
class ShortLinkResponseTest {

	private static final Instant CREATED = Instant.parse("2026-10-09T12:00:00Z");
	private static final Instant EXPIRES = CREATED.plus(Duration.ofMinutes(60));
	private static final ShortLink LINK = new ShortLink("abcde", "https://ejemplo.com", CREATED, EXPIRES);
	private static final String SHORT_URL = "https://sho.rt/abcde";
	private static final String QR_URL = "https://sho.rt/api/v1/links/abcde/qr";

	private static long secondsRemainingAt(Instant now) {
		return ShortLinkResponse.from(LINK, SHORT_URL, QR_URL, now).secondsRemaining();
	}

	@Test
	void copiesTheLinkAndItsPublicUrls() {
		ShortLinkResponse response = ShortLinkResponse.from(LINK, SHORT_URL, QR_URL, CREATED);

		assertThat(response.alias()).isEqualTo("abcde");
		assertThat(response.originalUrl()).isEqualTo("https://ejemplo.com");
		assertThat(response.createdAt()).isEqualTo(CREATED);
		assertThat(response.expiresAt()).isEqualTo(EXPIRES);
		assertThat(response.shortUrl()).isEqualTo(SHORT_URL);
		assertThat(response.qrUrl()).isEqualTo(QR_URL);
	}

	@Test
	void isTheFullTtlEvenIfTheResponseIsBuiltMillisecondsAfterCreation() {
		assertThat(secondsRemainingAt(CREATED)).isEqualTo(3600);
		assertThat(secondsRemainingAt(CREATED.plusMillis(1))).isEqualTo(3600);
		assertThat(secondsRemainingAt(CREATED.plusMillis(999))).isEqualTo(3600);
		assertThat(secondsRemainingAt(CREATED.plusMillis(1000))).isEqualTo(3599);
	}

	@Test
	void isStillPositiveOneMillisecondBeforeExpiring() {
		assertThat(secondsRemainingAt(EXPIRES.minusMillis(1))).isEqualTo(1);
	}

	@Test
	void isZeroAtAndAfterExpiration() {
		assertThat(secondsRemainingAt(EXPIRES)).isZero();
		assertThat(secondsRemainingAt(EXPIRES.plusSeconds(30))).isZero();
	}
}
