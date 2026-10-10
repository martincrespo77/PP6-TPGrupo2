package ar.edu.undef.fie.pp6.shortener.infrastructure.alias;

import ar.edu.undef.fie.pp6.shortener.config.AppProperties;
import ar.edu.undef.fie.pp6.shortener.domain.port.AliasGenerator;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Alias aleatorio de largo fijo sobre el alfabeto configurado, salteando palabras reservadas (D8-D10). */
@Component
public class RandomAliasGenerator implements AliasGenerator {

	static final int MAX_RESERVED_RETRIES = 100;

	private final int length;
	private final String alphabet;
	private final Set<String> reserved;
	private final RandomGenerator random;

	@Autowired
	public RandomAliasGenerator(AppProperties properties) {
		this(properties.alias(), new SecureRandom());
	}

	RandomAliasGenerator(AppProperties.Alias config, RandomGenerator random) {
		this.alphabet = requireValidAlphabet(config.alphabet());
		this.length = config.length();
		this.reserved = config.reserved().stream()
				.map(word -> word.toLowerCase(Locale.ROOT))
				.collect(Collectors.toUnmodifiableSet());
		this.random = random;
	}

	@Override
	public String generate() {
		for (int i = 0; i < MAX_RESERVED_RETRIES; i++) {
			String candidate = randomString();
			if (!reserved.contains(candidate.toLowerCase(Locale.ROOT))) {
				return candidate;
			}
		}
		throw new IllegalStateException("No se pudo generar un alias que no sea palabra reservada; revisar app.alias.*");
	}

	private String randomString() {
		StringBuilder alias = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			alias.append(alphabet.charAt(random.nextInt(alphabet.length())));
		}
		return alias.toString();
	}

	/** El alias viaja en la ruta /{alias}: solo letras y dígitos ASCII, sin repetidos (sesgarían la distribución). */
	private static String requireValidAlphabet(String alphabet) {
		if (!alphabet.matches("[A-Za-z0-9]+")) {
			throw new IllegalArgumentException("app.alias.alphabet solo admite letras y dígitos ASCII: " + alphabet);
		}
		if (alphabet.chars().distinct().count() != alphabet.length()) {
			throw new IllegalArgumentException("app.alias.alphabet tiene caracteres repetidos: " + alphabet);
		}
		return alphabet;
	}
}
