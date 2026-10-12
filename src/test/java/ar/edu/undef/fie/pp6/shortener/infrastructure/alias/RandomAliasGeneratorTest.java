package ar.edu.undef.fie.pp6.shortener.infrastructure.alias;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.undef.fie.pp6.shortener.config.AppProperties;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Grupo C: generación del alias (D8-D10)")
class RandomAliasGeneratorTest {

	private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
	private static final List<String> RESERVED = List.of("api", "admin", "index");

	private static AppProperties.Alias config(int length, String alphabet, List<String> reserved) {
		return new AppProperties.Alias(length, alphabet, reserved, 10);
	}

	/** Devuelve índices prefijados, para forzar qué alias se genera. */
	private static RandomGenerator scripted(String alphabet, String... aliases) {
		Queue<Integer> indexes = new ArrayDeque<>();
		for (String alias : aliases) {
			alias.chars().forEach(c -> indexes.add(alphabet.indexOf(c)));
		}
		return new RandomGenerator() {
			@Override
			public long nextLong() {
				throw new UnsupportedOperationException();
			}

			@Override
			public int nextInt(int bound) {
				return indexes.remove();
			}
		};
	}

	@Test
	void tc20_generatesAliasesOfConfiguredLengthUsingOnlyTheAlphabet() {
		RandomAliasGenerator generator = new RandomAliasGenerator(config(5, ALPHABET, RESERVED), new SecureRandom());
		Set<String> generated = new HashSet<>();

		for (int i = 0; i < 1_000; i++) {
			String alias = generator.generate();
			assertThat(alias).hasSize(5).matches("[" + ALPHABET + "]{5}");
			generated.add(alias);
		}

		assertThat(generated).as("1000 alias aleatorios de 31^5 posibles no deberían repetirse casi nunca").hasSizeGreaterThan(990);
	}

	@Test
	void tc20_alphabetHasNoAmbiguousCharacters() {
		assertThat(ALPHABET).hasSize(31).doesNotContain("0", "o", "1", "l", "i");
	}

	@Test
	void tc26_neverReturnsAReservedWord() {
		String alphabet = "abcdeinxz";
		RandomAliasGenerator generator = new RandomAliasGenerator(
				config(5, alphabet, RESERVED), scripted(alphabet, "index", "abcde"));

		assertThat(generator.generate()).isEqualTo("abcde");
	}

	@Test
	void tc26_reservedWordsAreComparedIgnoringCase() {
		String alphabet = "abcdeinxzINDEX";
		RandomAliasGenerator generator = new RandomAliasGenerator(
				config(5, alphabet, RESERVED), scripted(alphabet, "INDEX", "abcde"));

		assertThat(generator.generate()).isEqualTo("abcde");
	}

	@Test
	void failsFastWhenAlphabetHasCharactersOutsideTheRoutePattern() {
		assertThatThrownBy(() -> new RandomAliasGenerator(config(5, "abc-_", RESERVED), new SecureRandom()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("app.alias.alphabet");
	}

	@Test
	void failsFastWhenAlphabetHasRepeatedCharacters() {
		assertThatThrownBy(() -> new RandomAliasGenerator(config(5, "aabc", RESERVED), new SecureRandom()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("app.alias.alphabet");
	}

	@Test
	void givesUpWhenEveryCandidateIsReserved() {
		String[] alwaysApi = Collections.nCopies(RandomAliasGenerator.MAX_RESERVED_RETRIES, "api").toArray(String[]::new);
		RandomAliasGenerator generator = new RandomAliasGenerator(config(3, "api", RESERVED), scripted("api", alwaysApi));

		assertThatThrownBy(generator::generate).isInstanceOf(IllegalStateException.class);
	}
}
