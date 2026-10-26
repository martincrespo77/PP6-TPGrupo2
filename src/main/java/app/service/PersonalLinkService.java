package app.service;

import app.dto.ShortLinkResponse;
import app.exception.InvalidAliasException;
import app.exception.InvalidUrlException;
import app.exception.LinkNotFoundException;
import app.exception.PersonalAliasTakenException;
import app.exception.UsernameNotChosenException;
import app.mappings.AppUser;
import app.mappings.PersonalLink;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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
	private AliasGenerator aliasGenerator;

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
	 * Crea un enlace en el espacio del usuario. Sin alias, usa el más corto libre (1, 2, …). Si el alias está
	 * tomado por un enlace vencido, lo reasigna (como D6); si está vigente, falla.
	 */
	@Transactional
	public ShortLinkResponse create(String googleSubject, String url, String alias) {
		urlValidator.validate(url);
		boolean generated = alias == null || alias.isBlank();
		String requested = generated ? null : normalizeAlias(alias);
		AppUser owner = requireOwner(googleSubject);
		Instant now = clock.instant();
		String normalized = generated ? freeAlias(owner, now) : requested;
		String shortUrl = shortUrl(owner.getUsername(), normalized);
		if (shortUrl.equals(url)) {
			throw new InvalidUrlException("El enlace no puede apuntar a sí mismo");
		}
		releaseIfExpired(owner.getUsername(), normalized, now);
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

	/**
	 * Cambia el alias de un enlace vigente del usuario, sin tocar el destino ni el vencimiento. El alias
	 * nuevo sigue las mismas reglas que al crear: si lo tiene un enlace vencido se reasigna.
	 */
	@Transactional
	public ShortLinkResponse rename(String googleSubject, String currentAlias, String newAlias) {
		String normalized = normalizeAlias(newAlias);
		AppUser owner = requireOwner(googleSubject);
		Instant now = clock.instant();
		String current = currentAlias == null ? "" : currentAlias.toLowerCase(Locale.ROOT);
		PersonalLink link = Optional.ofNullable(find(owner.getUsername(), current))
				.filter(found -> !found.isExpired(now))
				.orElseThrow(() -> new LinkNotFoundException(current));
		if (!normalized.equals(current)) {
			releaseIfExpired(owner.getUsername(), normalized, now);
			link.changeAlias(normalized);
			try {
				em.flush();
			} catch (PersistenceException e) {
				throw new PersonalAliasTakenException(normalized);
			}
			log.info("Alias personal cambiado username={} {} -> {}", owner.getUsername(), current, normalized);
		}
		return ShortLinkResponse.from(link, shortUrl(owner.getUsername(), normalized), qrUrl(owner.getUsername(), normalized), now);
	}

	private AppUser requireOwner(String googleSubject) {
		AppUser owner = userService.require(googleSubject);
		if (!owner.isUsernameChosen()) {
			throw new UsernameNotChosenException();
		}
		return owner;
	}

	/** Libera el alias si lo tiene un enlace vencido; si lo tiene uno vigente, falla. */
	private void releaseIfExpired(String username, String alias, Instant now) {
		PersonalLink existing = find(username, alias);
		if (existing == null) {
			return;
		}
		if (!existing.isExpired(now)) {
			throw new PersonalAliasTakenException(alias);
		}
		em.remove(existing);
		em.flush();
	}

	/** El alias más corto libre en el espacio del usuario, con el mismo generador que los públicos (1, 2, …). */
	private String freeAlias(AppUser owner, Instant now) {
		Set<String> live = new HashSet<>(em.createQuery(
						"select p.alias from PersonalLink p where p.owner = :owner and p.expiresAt > :now", String.class)
				.setParameter("owner", owner)
				.setParameter("now", now)
				.getResultList());
		return aliasGenerator.generate(live);
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

	/**
	 * Enlace vigente pedido con el nombre anterior de su dueño, mientras ese nombre siga reservado
	 * (D56). Después, el nombre anterior ya no lleva a ningún enlace de esa cuenta.
	 */
	public Optional<MovedLink> findMoved(String previousUsername, String alias) {
		Instant now = clock.instant();
		return userService.findByReservedPreviousUsername(previousUsername)
				.flatMap(owner -> Optional.ofNullable(find(owner.getUsername(), alias.toLowerCase(Locale.ROOT)))
						.filter(link -> !link.isExpired(now))
						.map(link -> new MovedLink(shortUrl(owner.getUsername(), link.getAlias()), link.getOriginalUrl(),
								owner.getPreviousUsernameReleasedAt())));
	}

	/** @param oldNameReleasedAt cuándo deja de funcionar la dirección con el nombre anterior */
	public record MovedLink(String newShortUrl, String targetUrl, Instant oldNameReleasedAt) {
	}

	/**
	 * Si el usuario ya tiene un enlace vigente a esa URL, lo renueva app.link.ttl desde ahora (lo usa
	 * la extensión, como D12 para los públicos). Con varios, el más reciente.
	 */
	@Transactional
	public Optional<ShortLinkResponse> renew(String googleSubject, String url) {
		urlValidator.validate(url);
		AppUser owner = requireOwner(googleSubject);
		Instant now = clock.instant();
		Optional<PersonalLink> link = em.createQuery("""
						select p from PersonalLink p where p.owner = :owner and p.originalUrl = :url and p.expiresAt > :now
						order by p.createdAt desc""", PersonalLink.class)
				.setParameter("owner", owner)
				.setParameter("url", url)
				.setParameter("now", now)
				.setMaxResults(1)
				.getResultStream()
				.findFirst();
		link.ifPresent(existing -> existing.extendUntil(now.plus(ttl)));
		return link.map(existing -> ShortLinkResponse.from(existing, shortUrl(owner.getUsername(), existing.getAlias()),
				qrUrl(owner.getUsername(), existing.getAlias()), now));
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
