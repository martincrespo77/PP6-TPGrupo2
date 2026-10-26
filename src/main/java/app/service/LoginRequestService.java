package app.service;

import app.exception.LoginRequestNotFoundException;
import app.mappings.AppUser;
import app.mappings.LoginRequest;
import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingreso de una computadora escaneando un QR con el celular (como WhatsApp Web). La computadora
 * crea el pedido y guarda el código en su sesión; el celular, logueado con Google, lo aprueba; la
 * computadora lo consume una sola vez y recién ahí su sesión queda autenticada.
 */
@Service
public class LoginRequestService {

	private static final Logger log = LoggerFactory.getLogger(LoginRequestService.class);

	private static final int CODE_BYTES = 32;

	public enum State { PENDING, APPROVED, EXPIRED }

	/** @param googleSubject usuario que aprobó; solo viene con APPROVED */
	public record Poll(State state, String googleSubject, Instant expiresAt) {
	}

	@Autowired
	private EntityManager em;

	@Autowired
	private UserService userService;

	@Autowired
	private Clock clock;

	@Value("${app.base-url}")
	private String baseUrl;

	@Value("${app.login.ttl}")
	private Duration ttl;

	private final SecureRandom random = new SecureRandom();

	@Transactional
	public LoginRequest create() {
		Instant now = clock.instant();
		LoginRequest request = new LoginRequest(randomCode(), now, now.plus(ttl));
		em.persist(request);
		return request;
	}

	/** Dirección que codifica el QR; la abre el celular. */
	public String approvalUrl(String code) {
		return baseUrl.replaceAll("/+$", "") + "/l/" + code;
	}

	/**
	 * Aprueba un pedido pendiente y vigente. El UPDATE condicionado hace que dos aprobaciones
	 * simultáneas no puedan ganar las dos. Renueva el vencimiento para que la computadora tenga
	 * tiempo de consumirlo.
	 */
	@Transactional
	public void approve(String code, String googleSubject) {
		AppUser user = userService.require(googleSubject);
		Instant now = clock.instant();
		int updated = em.createQuery("""
				update LoginRequest r set r.status = :approved, r.user = :user, r.expiresAt = :expiresAt
				where r.code = :code and r.status = :pending and r.expiresAt > :now""")
				.setParameter("approved", LoginRequest.Status.APPROVED)
				.setParameter("user", user)
				.setParameter("expiresAt", now.plus(ttl))
				.setParameter("code", code)
				.setParameter("pending", LoginRequest.Status.PENDING)
				.setParameter("now", now)
				.executeUpdate();
		if (updated != 1) {
			throw new LoginRequestNotFoundException();
		}
		log.info("Ingreso por QR aprobado por usuario id={}", user.getId());
	}

	/** Estado visto desde la computadora. Un pedido aprobado se consume en la primera consulta. */
	@Transactional
	public Poll poll(String code) {
		Instant now = clock.instant();
		LoginRequest request = code == null ? null : em.find(LoginRequest.class, code);
		if (request == null || request.isExpired(now) || request.getStatus() == LoginRequest.Status.CONSUMED) {
			return new Poll(State.EXPIRED, null, null);
		}
		if (request.getStatus() == LoginRequest.Status.PENDING) {
			return new Poll(State.PENDING, null, request.getExpiresAt());
		}
		int consumed = em.createQuery("update LoginRequest r set r.status = :consumed where r.code = :code and r.status = :approved")
				.setParameter("consumed", LoginRequest.Status.CONSUMED)
				.setParameter("code", code)
				.setParameter("approved", LoginRequest.Status.APPROVED)
				.executeUpdate();
		if (consumed != 1) {
			return new Poll(State.EXPIRED, null, null);
		}
		return new Poll(State.APPROVED, request.getUser().getGoogleSubject(), request.getExpiresAt());
	}

	@Scheduled(cron = "${app.cleanup.cron}")
	@Transactional
	public int purgeExpired() {
		int deleted = em.createQuery("delete from LoginRequest r where r.expiresAt <= :now")
				.setParameter("now", clock.instant())
				.executeUpdate();
		log.info("Limpieza de pedidos de ingreso vencidos: {} borrado(s)", deleted);
		return deleted;
	}

	private String randomCode() {
		byte[] bytes = new byte[CODE_BYTES];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
