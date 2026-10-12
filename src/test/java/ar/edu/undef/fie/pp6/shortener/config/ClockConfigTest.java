package ar.edu.undef.fie.pp6.shortener.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

	private final Clock clock = new ClockConfig().clock();

	@Test
	void usesUtc() {
		assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
	}

	@Test
	void ticksInWholeMillisecondsSoInstantsMatchWhatTheDatabaseStores() {
		for (int i = 0; i < 1_000; i++) {
			Instant now = clock.instant();
			assertThat(now.getNano() % 1_000_000).isZero();
		}
	}
}
