package app.service;

import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Alias más corto disponible: el menor número n ≥ 1 escrito en base N con el alfabeto configurado
 * (1, 2, …, z, 10, 11, …), salteando los ocupados y las palabras reservadas (D8-D10).
 * No garantiza que siga libre al guardarlo: de eso se ocupa LinkService, que reintenta.
 */
@Component
public class AliasGenerator {

	/** El primer carácter hace de cero: nunca aparece solo ni a la izquierda. */
	@Value("${app.alias.alphabet}")
	String alphabet;

	@Value("${app.alias.reserved}")
	List<String> reserved;

	/** @param taken alias que no se pueden asignar (vigentes o descartados) */
	public String generate(Set<String> taken) {
		// Entre los primeros |taken| + |reserved| + 1 números siempre queda al menos uno libre.
		long limit = taken.size() + reserved.size() + 1L;
		for (long n = 1; n <= limit; n++) {
			String candidate = encode(n);
			if (!taken.contains(candidate) && !isReserved(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("No se encontró un alias libre; revisar app.alias.*");
	}

	String encode(long n) {
		int base = alphabet.length();
		if (base < 2) {
			throw new IllegalStateException("app.alias.alphabet necesita al menos dos caracteres");
		}
		StringBuilder alias = new StringBuilder();
		for (long value = n; value > 0; value /= base) {
			alias.append(alphabet.charAt((int) (value % base)));
		}
		return alias.reverse().toString();
	}

	private boolean isReserved(String candidate) {
		return reserved.stream().anyMatch(candidate::equalsIgnoreCase);
	}
}
