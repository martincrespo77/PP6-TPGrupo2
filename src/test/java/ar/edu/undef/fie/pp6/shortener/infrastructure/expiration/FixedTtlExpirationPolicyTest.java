package ar.edu.undef.fie.pp6.shortener.infrastructure.expiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FixedTtlExpirationPolicyTest {

	private static final Instant CREATED_AT = Instant.parse("2026-10-09T12:00:00Z");

	@Test
	void expiresSixtyMinutesAfterCreationWithDefaultTtl() {
		FixedTtlExpirationPolicy policy = new FixedTtlExpirationPolicy(Duration.ofMinutes(60));

		assertThat(policy.expirationFor(CREATED_AT)).isEqualTo(Instant.parse("2026-10-09T13:00:00Z"));
	}

	@Test
	void usesTheConfiguredTtl() {
		FixedTtlExpirationPolicy policy = new FixedTtlExpirationPolicy(Duration.ofSeconds(90));

		assertThat(policy.expirationFor(CREATED_AT)).isEqualTo(CREATED_AT.plusSeconds(90));
	}

	@Test
	void failsFastWithZeroOrNegativeTtl() {
		assertThatThrownBy(() -> new FixedTtlExpirationPolicy(Duration.ZERO))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("app.link.ttl");
		assertThatThrownBy(() -> new FixedTtlExpirationPolicy(Duration.ofMinutes(-1)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
