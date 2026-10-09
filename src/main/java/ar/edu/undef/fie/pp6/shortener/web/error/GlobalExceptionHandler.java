package ar.edu.undef.fie.pp6.shortener.web.error;

import ar.edu.undef.fie.pp6.shortener.domain.exception.AliasUnavailableException;
import ar.edu.undef.fie.pp6.shortener.domain.exception.InvalidUrlException;
import ar.edu.undef.fie.pp6.shortener.web.api.LinkApiController;
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
@RestControllerAdvice(basePackageClasses = LinkApiController.class)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String RETRY_AFTER_SECONDS = "1";

	@ExceptionHandler(InvalidUrlException.class)
	ProblemDetail invalidUrl(InvalidUrlException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
		problem.setTitle("URL inválida");
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

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
				"El cuerpo debe ser un JSON como {\"url\": \"https://...\"}");
		problem.setTitle("Pedido mal formado");
		return handleExceptionInternal(ex, problem, headers, status, request);
	}
}
