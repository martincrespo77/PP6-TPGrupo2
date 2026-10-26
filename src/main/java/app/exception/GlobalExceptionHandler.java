package app.exception;

import app.controller.AccountController;
import app.controller.LinkController;
import app.controller.LoginRequestController;
import app.controller.UserLinkController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Errores de la API en formato ProblemDetail (RFC 7807, D26). Limitado a los controladores de la API
 * para que el redirect y las páginas estáticas sigan respondiendo HTML.
 */
@RestControllerAdvice(assignableTypes = { LinkController.class, AccountController.class,
		LoginRequestController.class, UserLinkController.class })
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String RETRY_AFTER_SECONDS = "1";

	@ExceptionHandler(InvalidUrlException.class)
	ProblemDetail invalidUrl(InvalidUrlException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
		problem.setTitle("URL inválida");
		return problem;
	}

	@ExceptionHandler(InvalidQrSizeException.class)
	ProblemDetail invalidQrSize(InvalidQrSizeException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
		problem.setTitle("Tamaño inválido");
		return problem;
	}

	@ExceptionHandler(LinkNotFoundException.class)
	ProblemDetail linkNotFound(LinkNotFoundException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Este enlace expiró o no existe");
		problem.setTitle("Enlace no disponible");
		return problem;
	}

	@ExceptionHandler(AliasUnavailableException.class)
	ResponseEntity<ProblemDetail> aliasUnavailable(AliasUnavailableException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"No se pudo generar el enlace en este momento. Probá de nuevo en unos segundos.");
		problem.setTitle("Servicio no disponible");
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
				.body(problem);
	}

	@ExceptionHandler(InvalidUsernameException.class)
	ProblemDetail invalidUsername(InvalidUsernameException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
		problem.setTitle("Nombre de usuario inválido");
		return problem;
	}

	@ExceptionHandler(UsernameUnavailableException.class)
	ProblemDetail usernameUnavailable(UsernameUnavailableException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Ese nombre ya lo usa otra persona. Probá con otro");
		problem.setTitle("Nombre de usuario en uso");
		return problem;
	}

	@ExceptionHandler(UsernameChangeLockedException.class)
	ProblemDetail usernameChangeLocked(UsernameChangeLockedException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Tu nombre anterior \"" + e.getPreviousUsername()
						+ "\" sigue reservado: hasta que se libere solo podés volver a él");
		problem.setTitle("Cambio de nombre en curso");
		problem.setProperty("previousUsername", e.getPreviousUsername());
		problem.setProperty("releasedAt", e.getReleasedAt());
		return problem;
	}

	@ExceptionHandler(UsernameNotChosenException.class)
	ProblemDetail usernameNotChosen(UsernameNotChosenException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Antes de crear enlaces elegí tu nombre de usuario");
		problem.setTitle("Falta elegir el nombre de usuario");
		return problem;
	}

	@ExceptionHandler(InvalidAliasException.class)
	ProblemDetail invalidAlias(InvalidAliasException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
		problem.setTitle("Alias inválido");
		return problem;
	}

	@ExceptionHandler(PersonalAliasTakenException.class)
	ProblemDetail personalAliasTaken(PersonalAliasTakenException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Ya tenés un enlace vigente con ese alias. Elegí otro o esperá a que venza");
		problem.setTitle("Alias en uso");
		return problem;
	}

	@ExceptionHandler(LoginRequestNotFoundException.class)
	ProblemDetail loginRequestNotFound(LoginRequestNotFoundException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
				"El código venció o ya se usó. Escaneá el QR que se ve ahora en la computadora");
		problem.setTitle("Código no disponible");
		return problem;
	}

	@ExceptionHandler(UserNotFoundException.class)
	ProblemDetail userNotFound(UserNotFoundException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Volvé a iniciar sesión");
		problem.setTitle("Sesión no válida");
		return problem;
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
				"El cuerpo debe ser un JSON como {\"url\": \"https://...\"}");
		problem.setTitle("Pedido mal formado");
		return handleExceptionInternal(ex, problem, headers, status, request);
	}
}
