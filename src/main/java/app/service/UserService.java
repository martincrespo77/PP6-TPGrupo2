package app.service;

import app.exception.InvalidUsernameException;
import app.exception.UserNotFoundException;
import app.exception.UsernameChangeLockedException;
import app.exception.UsernameUnavailableException;
import app.mappings.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alta de usuarios en su primer ingreso con Google y elección del nombre de usuario. */
@Service
public class UserService {

	private static final Logger log = LoggerFactory.getLogger(UserService.class);

	/** Prefijo de los nombres aleatorios del alta: nadie puede elegir uno así, para obligar a cambiarlo. */
	static final String RANDOM_PREFIX = "usuario-";
	private static final String RANDOM_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
	private static final int RANDOM_LENGTH = 8;

	/** 3 a 20 caracteres: minúsculas, dígitos y guiones, sin guion al principio ni al final. */
	private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9-]{1,18}[a-z0-9]");

	@Autowired
	private EntityManager em;

	@Autowired
	private Clock clock;

	@Value("${app.user.reserved}")
	private List<String> reserved;

	@Value("${app.user.rename-grace}")
	private Duration renameGrace;

	private final Random random = new SecureRandom();

	/**
	 * Devuelve el usuario de esa cuenta de Google y lo da de alta si es su primer ingreso, con un nombre
	 * aleatorio que tiene que reemplazar.
	 *
	 * @param email correo informado por Google, o null si la sesión no lo trae (ingreso por QR)
	 */
	@Transactional
	public AppUser registerIfNew(String googleSubject, String email) {
		AppUser user = findBySubject(googleSubject).orElse(null);
		if (user == null) {
			user = new AppUser(googleSubject, email, randomUsername(), clock.instant());
			em.persist(user);
			log.info("Usuario nuevo username={}", user.getUsername());
		} else if (email != null && !email.equals(user.getEmail())) {
			user.updateEmail(email);
		}
		return user;
	}

	public Optional<AppUser> findBySubject(String googleSubject) {
		return em.createQuery("select u from AppUser u where u.googleSubject = :subject", AppUser.class)
				.setParameter("subject", googleSubject)
				.getResultList()
				.stream()
				.findFirst();
	}

	public AppUser require(String googleSubject) {
		return findBySubject(googleSubject).orElseThrow(UserNotFoundException::new);
	}

	public Optional<AppUser> findByUsername(String username) {
		return em.createQuery("select u from AppUser u where u.username = :username", AppUser.class)
				.setParameter("username", username.toLowerCase(Locale.ROOT))
				.getResultList()
				.stream()
				.findFirst();
	}

	/** @return el dueño de un nombre anterior que todavía está reservado */
	public Optional<AppUser> findByReservedPreviousUsername(String username) {
		return findByReservedPreviousUsername(username.toLowerCase(Locale.ROOT), clock.instant());
	}

	/**
	 * Nombres que ocupan /{nombre}: los actuales y los anteriores todavía reservados. El generador de
	 * alias públicos los saltea, así un enlace público nunca toma la dirección de un usuario (D54).
	 */
	public Set<String> namesInUse(Instant now) {
		Set<String> names = new HashSet<>(em.createQuery("select u.username from AppUser u", String.class).getResultList());
		names.addAll(em.createQuery("select u.previousUsername from AppUser u where u.previousUsernameReleasedAt > :now", String.class)
				.setParameter("now", now)
				.getResultList());
		return names;
	}

	public boolean isNameInUse(String name, Instant now) {
		return findByUsername(name).isPresent() || findByReservedPreviousUsername(name, now).isPresent();
	}

	/**
	 * Elige o cambia el nombre de usuario (en minúsculas). El primero reemplaza al aleatorio del alta.
	 * Cambiar uno elegido deja el anterior reservado app.user.rename-grace para esta cuenta: mientras
	 * tanto solo se puede volver a él (D55). La unicidad de los dos campos la garantiza también la base.
	 */
	@Transactional
	public AppUser chooseUsername(String googleSubject, String username) {
		String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
		validate(normalized);
		AppUser user = require(googleSubject);
		if (normalized.equals(user.getUsername())) {
			return user;
		}
		Instant now = clock.instant();
		releaseExpiredReservations(now);
		boolean reverting = user.hasReservedPreviousUsername(now) && normalized.equals(user.getPreviousUsername());
		if (user.hasReservedPreviousUsername(now) && !reverting) {
			throw new UsernameChangeLockedException(user.getPreviousUsername(), user.getPreviousUsernameReleasedAt());
		}
		if (!reverting) {
			checkAvailable(normalized, now);
		}
		if (user.isUsernameChosen()) {
			user.rename(normalized, now.plus(renameGrace));
		} else {
			user.chooseUsername(normalized);
		}
		try {
			em.flush();
		} catch (PersistenceException e) {
			throw new UsernameUnavailableException(normalized);
		}
		log.info("Usuario id={} eligió username={} (anterior reservado: {})", user.getId(), normalized, user.getPreviousUsername());
		return user;
	}

	/** Libera en lote los nombres anteriores vencidos. Que estén libres ya lo garantiza la lectura. */
	@Scheduled(cron = "${app.cleanup.cron}")
	@Transactional
	public int purgeExpiredReservations() {
		int released = releaseExpiredReservations(clock.instant());
		log.info("Nombres anteriores liberados: {}", released);
		return released;
	}

	private int releaseExpiredReservations(Instant now) {
		return em.createQuery("""
				update AppUser u set u.previousUsername = null, u.previousUsernameReleasedAt = null
				where u.previousUsername is not null and u.previousUsernameReleasedAt <= :now""")
				.setParameter("now", now)
				.executeUpdate();
	}

	private Optional<AppUser> findByReservedPreviousUsername(String username, Instant now) {
		return em.createQuery("""
				select u from AppUser u where u.previousUsername = :username and u.previousUsernameReleasedAt > :now""", AppUser.class)
				.setParameter("username", username)
				.setParameter("now", now)
				.getResultList()
				.stream()
				.findFirst();
	}

	/** Ocupado: es el nombre de otro, el anterior reservado de otro, o un enlace público vigente (/{nombre}). */
	private void checkAvailable(String name, Instant now) {
		boolean publicLink = em.createQuery("select count(s) from ShortLink s where s.alias = :name and s.expiresAt > :now", Long.class)
				.setParameter("name", name)
				.setParameter("now", now)
				.getSingleResult() > 0;
		if (isNameInUse(name, now) || publicLink) {
			throw new UsernameUnavailableException(name);
		}
	}

	private void validate(String username) {
		if (!USERNAME.matcher(username).matches()) {
			throw new InvalidUsernameException(
					"El nombre debe tener entre 3 y 20 caracteres: letras minúsculas, números y guiones (sin guion al principio ni al final)");
		}
		if (username.startsWith(RANDOM_PREFIX)) {
			throw new InvalidUsernameException("Elegí un nombre propio: los que empiezan con \"" + RANDOM_PREFIX + "\" son los aleatorios");
		}
		if (reserved.stream().anyMatch(username::equalsIgnoreCase)) {
			throw new InvalidUsernameException("Ese nombre está reservado. Probá con otro");
		}
	}

	private String randomUsername() {
		StringBuilder name = new StringBuilder(RANDOM_PREFIX);
		for (int i = 0; i < RANDOM_LENGTH; i++) {
			name.append(RANDOM_ALPHABET.charAt(random.nextInt(RANDOM_ALPHABET.length())));
		}
		return name.toString();
	}
}
