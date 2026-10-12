package app.service;

import app.dto.ShortLinkResponse;
import app.exception.AliasUnavailableException;
import app.mappings.ShortLink;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Reglas de negocio de los enlaces: acortar, resolver y borrar vencidos. Es dueño de sus consultas. */
@Service
public class LinkService {

	private static final Logger log = LoggerFactory.getLogger(LinkService.class);

	@Autowired
	private EntityManager em;

	@Autowired
	private TransactionTemplate transaction;

	@Autowired
	private UrlValidator urlValidator;

	@Autowired
	private AliasGenerator aliasGenerator;

	@Autowired
	private Clock clock;

	/** Dominio con el que se arman la URL corta y el QR: nunca sale de la petición (I4, D23). */
	@Value("${app.base-url}")
	private String baseUrl;

	@Value("${app.link.ttl}")
	private Duration ttl;

	@Value("${app.alias.max-attempts}")
	private int maxAttempts;

	/**
	 * Acorta una URL. No es @Transactional a propósito: después de una violación de PK la sesión de
	 * Hibernate queda inutilizable, así que cada intento corre en su propia transacción.
	 */
	public ShortLinkResponse shorten(String url) {
		urlValidator.validate(url);
		Instant now = clock.instant();
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			String alias = aliasGenerator.generate();
			if (shortUrl(alias).equals(url)) {
				continue;
			}
			try {
				ShortLink created = transaction.execute(status -> tryCreate(alias, url, now));
				if (created != null) {
					log.info("Enlace creado alias={} expira={}", alias, created.getExpiresAt());
					return ShortLinkResponse.from(created, shortUrl(alias), qrUrl(alias), now);
				}
			} catch (EntityExistsException | ConstraintViolationException e) {
				log.debug("Alias {} tomado por otra transacción; se reintenta", alias);
			}
		}
		log.warn("Sin alias disponible después de {} intentos", maxAttempts);
		throw new AliasUnavailableException(maxAttempts);
	}

	/** @return el enlace creado, o null si el alias está ocupado por un enlace vigente */
	private ShortLink tryCreate(String alias, String url, Instant now) {
		ShortLink existing = find(alias);
		if (existing != null) {
			if (!existing.isExpired(now)) {
				return null;
			}
			// Vencido: se borra y el alias se reasigna en la misma transacción (D6).
			em.remove(existing);
			em.flush();
		}
		ShortLink link = new ShortLink(alias, url, now, now.plus(ttl));
		// persist nunca sobrescribe (I2). Sin flush, un alias duplicado recién fallaría al commit.
		em.persist(link);
		em.flush();
		return link;
	}

	ShortLink find(String alias) {
		return em.find(ShortLink.class, alias);
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

	/**
	 * El enlace, si existe y no venció. Única regla de "vigente" para el redirect y el QR: el
	 * vencimiento se evalúa en cada lectura, sin depender de la limpieza programada (I1, D2).
	 */
	public Optional<ShortLink> findLive(String alias) {
		String normalized = alias.toLowerCase(Locale.ROOT);
		Instant now = clock.instant();
		return Optional.ofNullable(find(normalized))
				.filter(link -> !link.isExpired(now));
	}

	/**
	 * Borra físicamente los enlaces vencidos (D5), todos los días según app.cleanup.cron. Solo libera
	 * espacio: que un vencido no redirija ya lo garantiza la lectura (I1).
	 *
	 * @return cantidad de enlaces borrados
	 */
	@Scheduled(cron = "${app.cleanup.cron}")
	@Transactional
	public int purgeExpired() {
		int deleted = em.createQuery("delete from ShortLink s where s.expiresAt <= :now")
				.setParameter("now", clock.instant())
				.executeUpdate();
		log.info("Limpieza de vencidos: {} enlace(s) borrado(s)", deleted);
		return deleted;
	}

	public String shortUrl(String alias) {
		return baseUrl() + "/" + alias;
	}

	private String qrUrl(String alias) {
		return baseUrl() + "/api/v1/links/" + alias + "/qr";
	}

	private String baseUrl() {
		return baseUrl.replaceAll("/+$", "");
	}
}
