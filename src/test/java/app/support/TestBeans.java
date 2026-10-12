package app.support;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionTemplate;

/** Reemplazos de test: un reloj que se mueve a mano y un generador de alias al que se le dictan alias. */
@TestConfiguration
public class TestBeans {

	public static final Instant START = Instant.parse("2026-10-09T12:00:00Z");

	@Bean
	@Primary
	MutableClock mutableClock() {
		return new MutableClock(START);
	}

	@Bean
	@Primary
	ScriptedAliasGenerator scriptedAliasGenerator() {
		return new ScriptedAliasGenerator();
	}

	@Bean
	TestDb testDb(EntityManager em, TransactionTemplate transaction) {
		return new TestDb(em, transaction);
	}
}
