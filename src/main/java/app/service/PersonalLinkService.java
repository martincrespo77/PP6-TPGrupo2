package app.service;

import app.dto.ShortLinkResponse;
import app.exception.InvalidAliasException;
import app.exception.InvalidUrlException;
import app.exception.PersonalAliasTakenException;
import app.exception.UsernameNotChosenException;
import app.mappings.AppUser;
import app.mappings.PersonalLink;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enlaces con alias elegido en el espacio de un usuario: /{username}/{alias}. Mismo TTL que los públicos. */
@Service
public class PersonalLinkService {

	private static final Logger log = LoggerFactory.getLogger(PersonalLinkService.class);

	private static final Pattern ALIAS = Pattern.compile("[a-z0-9-]{1," + PersonalLink.MAX_ALIAS_LENGTH + "}");

	@Autowired
	private EntityManager em;

	@Autowired
	private UrlValidator urlValidator;

	@Autowired
	private UserService userService;

	@Autowired
	private Clock clock;

	@Value("${app.base-url}")
	private String baseUrl;

	@Value("${app.link.ttl}")
	private Duration ttl;

	/**
	 * Crea un enlace en el espacio del usuario. Si el alias está tomado por un enlace vencido, lo
	 * reasigna (como D6); si está vigente, falla.
	 */
	@Transactional
	public ShortLinkResponse create(String googleSubject, String url, String alias) {
		urlValidator.validate(url);
		String normalized = normalizeAlias(alias);
		AppUser owner = userService.require(googleSubject);
		if (!owner.isUsernameChosen()) {
			throw new UsernameNotChosenException();
		}
		String shortUrl = shortUrl(owner.getUsername(), normalized);
		if (shortUrl.equals(url)) {
			throw new InvalidUrlException("El enlace no puede apuntar a sí mismo");
		}
		Instant now = clock.instant();
		PersonalLink existing = find(owner.getUsername(), normalized);
		if (existing != null) {
			if (!existing.isExpired(now)) {
				throw new PersonalAliasTakenException(normalized);
			}
			em.remove(existing);
			em.flush();
		}
		PersonalLink link = new PersonalLink(owner, normalized, url, now, now.plus(ttl));
		try {
			em.persist(link);
			em.flush();
		} catch (PersistenceException e) {
			throw new PersonalAliasTakenException(normalized);
		}
		log.info("Enlace personal creado username={} alias={} expira={}", owner.getUsername(), normalized, link.getExpiresAt());
		return ShortLinkResponse.from(link, shortUrl, qrUrl(owner.getUsername(), normalized), now);
	}

	/** @return la URL destino si el enlace existe y no venció */
	public Optional<String> resolve(String username, String alias) {
		Optional<PersonalLink> link = findLive(username, alias);
		log.info("{} username={} alias={}", link.isPresent() ? "Redirección personal" : "Enlace personal no disponible", username, alias);
		return link.map(PersonalLink::getOriginalUrl);
	}

	public Optional<PersonalLink> findLive(String username, String alias) {
		Instant now = clock.instant();
		return Optional.ofNullable(find(username.toLowerCase(Locale.ROOT), alias.toLowerCase(Locale.ROOT)))
				.filter(link -> !link.isExpired(now));
	}

	private PersonalLink find(String username, String alias) {
		return em.createQuery("select p from PersonalLink p where p.owner.username = :username and p.alias = :alias",
						PersonalLink.class)
				.setParameter("username", username)
				.setParameter("alias", alias)
				.getResultList()
				.stream()
				.findFirst()
				.orElse(null);
	}

	/** Borra los enlaces personales vencidos, con el mismo cron que los públicos (D5). */
	@Scheduled(cron = "${app.cleanup.cron}")
	@Transactional
	public int purgeExpired() {
		int deleted = em.createQuery("delete from PersonalLink p where p.expiresAt <= :now")
				.setParameter("now", clock.instant())
				.executeUpdate();
		log.info("Limpieza de enlaces personales vencidos: {} borrado(s)", deleted);
		return deleted;
	}

	public String shortUrl(String username, String alias) {
		return namespaceUrl(username) + alias;
	}

	public String namespaceUrl(String username) {
		return baseUrl() + "/" + username + "/";
	}

	private String qrUrl(String username, String alias) {
		return baseUrl() + "/api/v1/users/" + username + "/links/" + alias + "/qr";
	}

	private String baseUrl() {
		return baseUrl.replaceAll("/+$", "");
	}

	private static String normalizeAlias(String alias) {
		String normalized = alias == null ? "" : alias.trim().toLowerCase(Locale.ROOT);
		if (!ALIAS.matcher(normalized).matches()) {
			throw new InvalidAliasException("El alias debe tener entre 1 y " + PersonalLink.MAX_ALIAS_LENGTH
					+ " caracteres: letras, números y guiones");
		}
		return normalized;
	}
}
