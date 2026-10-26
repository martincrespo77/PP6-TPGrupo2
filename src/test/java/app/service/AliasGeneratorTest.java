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

	private static final String ALPHABET = "0123456789abcdefghjkmnpqrstuvwxyz";

	private static AliasGenerator generator(String alphabet, String... reserved) {
		AliasGenerator generator = new AliasGenerator();
		generator.alphabet = alphabet;
		generator.reserved = List.of(reserved);
		return generator;
	}

	@Test
	void tc20_firstAliasIsOne() {
		assertThat(generator(ALPHABET).generate(Set.of())).isEqualTo("1");
	}

	@Test
	void tc20_aliasesGrowInLengthOnlyAfterEveryShorterOneIsUsed() {
		AliasGenerator generator = generator(ALPHABET);

		assertThat(generator.encode(1)).isEqualTo("1");
		assertThat(generator.encode(9)).isEqualTo("9");
		assertThat(generator.encode(10)).isEqualTo("a");
		assertThat(generator.encode(32)).isEqualTo("z");
		assertThat(generator.encode(33)).isEqualTo("10");
		assertThat(generator.encode(33 * 33 - 1)).isEqualTo("zz");
		assertThat(generator.encode(33 * 33)).isEqualTo("100");
	}

	@Test
	void tc20_withOnlyDigitsTheAliasesAreDecimalNumbers() {
		AliasGenerator generator = generator("0123456789");

		assertThat(generator.encode(7)).isEqualTo("7");
		assertThat(generator.encode(42)).isEqualTo("42");
		assertThat(generator.encode(100)).isEqualTo("100");
	}

	@Test
	void tc20_usesOnlyTheAlphabetAndNeverRepeats() {
		AliasGenerator generator = generator(ALPHABET);
		Set<String> taken = new HashSet<>();

		for (int i = 0; i < 2_000; i++) {
			String alias = generator.generate(taken);
			assertThat(alias).matches("[" + ALPHABET + "]+").doesNotStartWith("0");
			assertThat(taken.add(alias)).as("alias repetido: %s", alias).isTrue();
		}
		assertThat(taken).as("los 32 de un carácter se usan antes que cualquiera de dos")
				.filteredOn(alias -> alias.length() == 1).hasSize(32);
	}

	@Test
	void tc20_alphabetHasNoAmbiguousLetters() {
		assertThat(ALPHABET).hasSize(33).doesNotContain("o", "l", "i");
	}

	@Test
	void reusesTheSmallestReleasedAlias() {
		AliasGenerator generator = generator(ALPHABET);

		assertThat(generator.generate(Set.of("1", "2", "4", "5"))).isEqualTo("3");
		assertThat(generator.generate(Set.of("2", "3"))).isEqualTo("1");
	}

	@Test
	void skipsTakenAliasesThatAreNotSequential() {
		assertThat(generator(ALPHABET).generate(Set.of("1", "xt3se"))).isEqualTo("2");
	}

	@Test
	void tc26_neverReturnsAReservedWord() {
		AliasGenerator generator = generator(ALPHABET, "1", "3");

		assertThat(generator.generate(Set.of())).isEqualTo("2");
		assertThat(generator.generate(Set.of("2"))).isEqualTo("4");
	}

	@Test
	void tc26_reservedWordsAreComparedIgnoringCase() {
		assertThat(generator("0AB", "a").generate(Set.of())).isEqualTo("B");
	}

	@Test
	void alwaysFindsAFreeAliasEvenWhenTheFirstOnesAreTakenOrReserved() {
		AliasGenerator generator = generator("01", "1", "10");
		Set<String> taken = Set.of("11", "100", "101");

		assertThat(generator.generate(taken)).isEqualTo("110");
	}

	@Test
	void rejectsAnAlphabetWithFewerThanTwoCharacters() {
		assertThatThrownBy(() -> generator("a").generate(Set.of())).isInstanceOf(IllegalStateException.class);
	}
}
