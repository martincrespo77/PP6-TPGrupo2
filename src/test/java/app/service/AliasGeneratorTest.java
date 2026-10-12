package app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Grupo C: generación del alias (D8-D10)")
class AliasGeneratorTest {

	private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

	private static AliasGenerator generator(int length, String alphabet, String... reserved) {
		AliasGenerator generator = new AliasGenerator();
		generator.length = length;
		generator.alphabet = alphabet;
		generator.reserved = List.of(reserved);
		return generator;
	}

	@Test
	void tc20_generatesAliasesOfConfiguredLengthUsingOnlyTheAlphabet() {
		AliasGenerator generator = generator(5, ALPHABET, "api", "admin", "index");
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
		// Con un alfabeto de dos letras y largo 1 solo existen "a" y "b": si "a" está reservada, siempre sale "b".
		AliasGenerator generator = generator(1, "ab", "a");

		for (int i = 0; i < 200; i++) {
			assertThat(generator.generate()).isEqualTo("b");
		}
	}

	@Test
	void tc26_reservedWordsAreComparedIgnoringCase() {
		AliasGenerator generator = generator(1, "AB", "a");

		for (int i = 0; i < 200; i++) {
			assertThat(generator.generate()).isEqualTo("B");
		}
	}

	@Test
	void givesUpWhenEveryCandidateIsReserved() {
		AliasGenerator generator = generator(3, "a", "aaa");

		assertThatThrownBy(generator::generate).isInstanceOf(IllegalStateException.class);
	}
}
