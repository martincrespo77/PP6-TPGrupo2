package app.service;

import app.exception.InvalidUsernameException;
import app.exception.UserNotFoundException;
import app.exception.UsernameUnavailableException;
import app.mappings.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

	Optional<AppUser> findByUsername(String username) {
		return em.createQuery("select u from AppUser u where u.username = :username", AppUser.class)
				.setParameter("username", username)
				.getResultList()
				.stream()
				.findFirst();
	}

	/** Cambia el nombre de usuario. Se guarda en minúsculas; la unicidad la garantiza la base. */
	@Transactional
	public AppUser chooseUsername(String googleSubject, String username) {
		String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
		validate(normalized);
		AppUser user = require(googleSubject);
		Optional<AppUser> owner = findByUsername(normalized);
		if (owner.isPresent() && !owner.get().getId().equals(user.getId())) {
			throw new UsernameUnavailableException(normalized);
		}
		user.chooseUsername(normalized);
		try {
			em.flush();
		} catch (PersistenceException e) {
			throw new UsernameUnavailableException(normalized);
		}
		log.info("Usuario id={} eligió username={}", user.getId(), normalized);
		return user;
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
