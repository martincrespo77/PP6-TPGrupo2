package app.service;

import app.dto.ShortLinkResponse;
import app.exception.AliasUnavailableException;
import app.mappings.ShortLink;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Reglas de negocio de los enlaces: acortar, renovar, resolver y borrar vencidos. Es dueño de sus consultas. */
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
	 * Acorta una URL con el alias más corto libre. No es @Transactional a propósito: después de una
	 * violación de PK la sesión de Hibernate queda inutilizable, así que cada intento corre en su
	 * propia transacción. Si dos pedidos simultáneos eligen el mismo alias, la PK rechaza al segundo,
	 * que reintenta con el siguiente libre (I2).
	 */
	public ShortLinkResponse shorten(String url) {
		urlValidator.validate(url);
		Instant now = clock.instant();
		Set<String> discarded = new HashSet<>();
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			Set<String> taken = transaction.execute(status -> liveAliases(now));
			taken.addAll(discarded);
			String alias = aliasGenerator.generate(taken);
			discarded.add(alias);
			if (shortUrl(alias).equals(url)) {
				continue;
			}
			try {
				ShortLink created = transaction.execute(status -> tryCreate(alias, url, now));
				if (created != null) {
					log.info("Enlace creado alias={} expira={}", alias, created.getExpiresAt());
					return response(created, now);
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

	/** Los vencidos no cuentan: su alias se puede reasignar aunque la fila siga (D6). */
	private Set<String> liveAliases(Instant now) {
		return new HashSet<>(em.createQuery("select s.alias from ShortLink s where s.expiresAt > :now", String.class)
				.setParameter("now", now)
				.getResultList());
	}

	/**
	 * Si la URL ya tiene un enlace vigente, lo renueva por app.link.ttl desde ahora (lo usa la
	 * extensión). Con varios vigentes para la misma URL, renueva el más reciente.
	 *
	 * @return el enlace renovado, o vacío si la URL no tiene ninguno vigente
	 */
	@Transactional
	public Optional<ShortLinkResponse> renew(String url) {
		urlValidator.validate(url);
		Instant now = clock.instant();
		Optional<ShortLink> link = em.createQuery(
						"select s from ShortLink s where s.originalUrl = :url and s.expiresAt > :now order by s.createdAt desc",
						ShortLink.class)
				.setParameter("url", url)
				.setParameter("now", now)
				.setMaxResults(1)
				.getResultStream()
				.findFirst();
		link.ifPresent(existing -> {
			existing.extendUntil(now.plus(ttl));
			log.info("Enlace renovado alias={} expira={}", existing.getAlias(), existing.getExpiresAt());
		});
		return link.map(existing -> response(existing, now));
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

	private ShortLinkResponse response(ShortLink link, Instant now) {
		return ShortLinkResponse.from(link, shortUrl(link.getAlias()), qrUrl(link.getAlias()), now);
	}

	private String qrUrl(String alias) {
		return baseUrl() + "/api/v1/links/" + alias + "/qr";
	}

	private String baseUrl() {
		return baseUrl.replaceAll("/+$", "");
	}
}
