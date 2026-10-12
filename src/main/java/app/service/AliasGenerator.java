package app.service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Alias aleatorio de largo fijo sobre el alfabeto configurado, salteando palabras reservadas (D8-D10).
 * No garantiza que esté libre: de eso se ocupa LinkService, que reintenta.
 */
@Component
public class AliasGenerator {

	static final int MAX_RESERVED_RETRIES = 100;

	@Value("${app.alias.length}")
	int length;

	@Value("${app.alias.alphabet}")
	String alphabet;

	@Value("${app.alias.reserved}")
	List<String> reserved;

	private final Random random = new SecureRandom();

	public String generate() {
		for (int i = 0; i < MAX_RESERVED_RETRIES; i++) {
			String candidate = randomString();
			if (!isReserved(candidate)) {
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

	private boolean isReserved(String candidate) {
		return reserved.stream().anyMatch(candidate::equalsIgnoreCase);
	}
}
