package app.controller;

import app.dto.LoginStatusResponse;
import app.exception.LoginRequestNotFoundException;
import app.mappings.LoginRequest;
import app.service.LoginRequestService;
import app.service.QrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingreso por QR. La computadora crea el pedido (el código queda solo en su sesión y en el QR), el
 * celular lo aprueba y la computadora consulta el estado hasta que su sesión queda iniciada.
 */
@Tag(name = "Ingreso por QR", description = "La computadora muestra un QR y el celular, logueado con Google, lo aprueba")
@RestController
@RequestMapping("/api/v1/login-requests")
public class LoginRequestController {

	static final String SESSION_CODE = "app.loginRequestCode";

	@Autowired
	private LoginRequestService loginRequestService;

	@Autowired
	private QrService qrService;

	@Autowired
	private CurrentUser currentUser;

	@Autowired
	private Clock clock;

	private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

	@Operation(summary = "Computadora: crea un código de ingreso nuevo y lo guarda en su sesión")
	@ApiResponse(responseCode = "201", description = "Código creado; el QR está en /current/qr")
	@PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public LoginStatusResponse create(HttpSession session) {
		LoginRequest request = loginRequestService.create();
		session.setAttribute(SESSION_CODE, request.getCode());
		return LoginStatusResponse.of(LoginRequestService.State.PENDING.name(), request.getExpiresAt(), clock.instant());
	}

	@Operation(summary = "Computadora: PNG del QR que hay que escanear con el celular")
	@ApiResponse(responseCode = "200", description = "Imagen PNG",
			content = @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary")))
	@ApiResponse(responseCode = "404", description = "Esta sesión no tiene un código")
	@GetMapping(value = "/current/qr", produces = MediaType.IMAGE_PNG_VALUE)
	public ResponseEntity<byte[]> qr(HttpSession session) {
		String code = (String) session.getAttribute(SESSION_CODE);
		if (code == null) {
			throw new LoginRequestNotFoundException();
		}
		return ResponseEntity.ok()
				.contentType(MediaType.IMAGE_PNG)
				.cacheControl(CacheControl.noStore())
				.body(qrService.qrForLogin(code));
	}

	@Operation(summary = "Computadora: estado del código; con APPROVED la sesión ya quedó iniciada")
	@ApiResponse(responseCode = "200", description = "PENDING, APPROVED o EXPIRED")
	@GetMapping(value = "/current", produces = MediaType.APPLICATION_JSON_VALUE)
	public LoginStatusResponse status(HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession();
		LoginRequestService.Poll poll = loginRequestService.poll((String) session.getAttribute(SESSION_CODE));
		if (poll.state() == LoginRequestService.State.APPROVED) {
			session.removeAttribute(SESSION_CODE);
			signIn(poll.googleSubject(), request, response);
		}
		return LoginStatusResponse.of(poll.state().name(), poll.expiresAt(), clock.instant());
	}

	@Operation(summary = "Celular: aprueba el ingreso de la computadora que muestra ese código")
	@ApiResponse(responseCode = "204", description = "Aprobado")
	@ApiResponse(responseCode = "401", description = "Sin sesión en el celular")
	@ApiResponse(responseCode = "404", description = "El código venció o ya se usó",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/{code:[A-Za-z0-9_-]{" + LoginRequest.CODE_LENGTH + "}}/approve")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void approve(@PathVariable String code, Authentication authentication) {
		currentUser.of(authentication);
		loginRequestService.approve(code, authentication.getName());
	}

	/** Nuevo id de sesión al autenticarse, para que un id robado antes del ingreso no sirva (session fixation). */
	private void signIn(String googleSubject, HttpServletRequest request, HttpServletResponse response) {
		request.changeSessionId();
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
				googleSubject, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
	}
}
