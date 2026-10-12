package ar.edu.undef.fie.pp6.shortener.application;

import ar.edu.undef.fie.pp6.shortener.config.AppProperties;
import ar.edu.undef.fie.pp6.shortener.domain.exception.AliasAlreadyTakenException;
import ar.edu.undef.fie.pp6.shortener.domain.exception.AliasUnavailableException;
import ar.edu.undef.fie.pp6.shortener.domain.model.ShortLink;
import ar.edu.undef.fie.pp6.shortener.domain.port.AliasGenerator;
import ar.edu.undef.fie.pp6.shortener.domain.port.ExpirationPolicy;
import ar.edu.undef.fie.pp6.shortener.domain.port.ShortLinkRepository;
import ar.edu.undef.fie.pp6.shortener.domain.port.UrlValidator;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Caso de uso "acortar una URL". No es @Transactional a propósito: después de una violación de PK
 * la sesión de Hibernate queda inutilizable, así que cada intento corre en su propia transacción.
 */
@Service
public class ShortenLinkService {

	private static final Logger log = LoggerFactory.getLogger(ShortenLinkService.class);

	private final UrlValidator urlValidator;
	private final AliasGenerator aliasGenerator;
	private final ExpirationPolicy expirationPolicy;
	private final ShortLinkRepository repository;
	private final LinkUrls linkUrls;
	private final TransactionTemplate transaction;
	private final Clock clock;
	private final int maxAttempts;

	public ShortenLinkService(UrlValidator urlValidator, AliasGenerator aliasGenerator,
			ExpirationPolicy expirationPolicy, ShortLinkRepository repository, LinkUrls linkUrls,
			PlatformTransactionManager transactionManager, Clock clock, AppProperties properties) {
		this.urlValidator = urlValidator;
		this.aliasGenerator = aliasGenerator;
		this.expirationPolicy = expirationPolicy;
		this.repository = repository;
		this.linkUrls = linkUrls;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
		this.maxAttempts = properties.alias().maxAttempts();
	}

	public ShortLink shorten(String url) {
		urlValidator.validate(url);
		Instant now = clock.instant();
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			String alias = aliasGenerator.generate();
			if (linkUrls.shortUrl(alias).equals(url)) {
				continue;
			}
			try {
				Optional<ShortLink> created = transaction.execute(status -> tryCreate(alias, url, now));
				if (created.isPresent()) {
					log.info("Enlace creado alias={} expira={}", alias, created.get().getExpiresAt());
					return created.get();
				}
			} catch (AliasAlreadyTakenException e) {
				log.debug("Alias {} tomado por otra transacción; se reintenta", alias);
			}
		}
		log.warn("Sin alias disponible después de {} intentos", maxAttempts);
		throw new AliasUnavailableException(maxAttempts);
	}

	private Optional<ShortLink> tryCreate(String alias, String url, Instant now) {
		Optional<ShortLink> existing = repository.findByAlias(alias);
		if (existing.isPresent()) {
			if (!existing.get().isExpired(now)) {
				return Optional.empty();
			}
			repository.deleteByAlias(alias);
		}
		ShortLink link = new ShortLink(alias, url, now, expirationPolicy.expirationFor(now));
		repository.persist(link);
		return Optional.of(link);
	}
}
