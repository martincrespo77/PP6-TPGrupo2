package ar.edu.undef.fie.pp6.shortener.application;

import ar.edu.undef.fie.pp6.shortener.domain.model.ShortLink;
import ar.edu.undef.fie.pp6.shortener.domain.port.ShortLinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Caso de uso "resolver un alias". Vencido e inexistente dan el mismo resultado vacío (D7): el
 * vencimiento se evalúa en cada lectura, sin depender del cron (I1, D2).
 */
@Service
public class ResolveLinkService {

	private static final Logger log = LoggerFactory.getLogger(ResolveLinkService.class);

	private final ShortLinkRepository repository;
	private final Clock clock;

	public ResolveLinkService(ShortLinkRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/** @return la URL destino si el alias existe y no venció */
	public Optional<String> resolve(String alias) {
		Optional<ShortLink> link = findLive(alias);
		if (link.isPresent()) {
			log.info("Redirección alias={}", link.get().getAlias());
		} else {
			log.info("Alias no disponible alias={}", alias);
		}
		return link.map(ShortLink::getOriginalUrl);
	}

	/** El enlace, si existe y no venció. Única regla de "vigente" para el redirect y el QR. */
	public Optional<ShortLink> findLive(String alias) {
		String normalized = alias.toLowerCase(Locale.ROOT);
		Instant now = clock.instant();
		return repository.findByAlias(normalized)
				.filter(link -> !link.isExpired(now));
	}
}
