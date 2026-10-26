package app.controller;

import app.DesktopSession;
import app.dto.AccountResponse;
import app.dto.ChangeAliasRequest;
import app.dto.ChooseUsernameRequest;
import app.dto.PersonalLinkRequest;
import app.dto.SessionResponse;
import app.dto.ShortLinkResponse;
import app.mappings.AppUser;
import app.service.PersonalLinkService;
import app.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cuenta del usuario con sesión iniciada: nombre de usuario y enlaces en su espacio. */
@Tag(name = "Cuenta", description = "Requiere sesión iniciada")
@RestController
@RequestMapping("/api/v1/me")
public class AccountController {

	@Autowired
	private UserService userService;

	@Autowired
	private PersonalLinkService personalLinkService;

	@Autowired
	private CurrentUser currentUser;

	@Autowired
	private DesktopSession desktopSession;

	@Operation(summary = "Datos de la cuenta; da de alta al usuario en su primer ingreso")
	@ApiResponse(responseCode = "200", description = "Cuenta")
	@ApiResponse(responseCode = "401", description = "Sin sesión")
	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public AccountResponse me(Authentication authentication) {
		return response(currentUser.of(authentication));
	}

	@Operation(summary = "Elige o cambia el nombre de usuario (el aleatorio del alta hay que cambiarlo); "
			+ "el anterior queda reservado unos minutos y mientras tanto solo se puede volver a él")
	@ApiResponse(responseCode = "200", description = "Nombre guardado")
	@ApiResponse(responseCode = "400", description = "Formato inválido o nombre reservado",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Otro usuario ya lo tiene, o hay un cambio de nombre en curso",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@PutMapping(value = "/username", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public AccountResponse chooseUsername(Authentication authentication, @RequestBody ChooseUsernameRequest request) {
		currentUser.of(authentication);
		return response(userService.chooseUsername(authentication.getName(), request.username()));
	}

	@Operation(summary = "Crea un enlace /{usuario}/{alias} que vence a los 60 minutos; sin alias, se genera uno aleatorio")
	@ApiResponse(responseCode = "200", description = "Con reuseExisting=true: ya tenías un enlace vigente a esa URL y se renovó 60 minutos")
	@ApiResponse(responseCode = "201", description = "Enlace creado")
	@ApiResponse(responseCode = "400", description = "URL o alias inválidos",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Alias en uso, o falta elegir el nombre de usuario",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping(value = "/links", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ShortLinkResponse> createLink(Authentication authentication, @RequestBody PersonalLinkRequest request) {
		currentUser.of(authentication);
		if (request.reuse()) {
			Optional<ShortLinkResponse> renewed = personalLinkService.renew(authentication.getName(), request.url());
			if (renewed.isPresent()) {
				return ResponseEntity.ok(renewed.get());
			}
		}
		ShortLinkResponse response = personalLinkService.create(authentication.getName(), request.url(), request.alias());
		return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
	}

	@Operation(summary = "Cambia el alias de un enlace vigente; el destino y el vencimiento no cambian")
	@ApiResponse(responseCode = "200", description = "Alias cambiado")
	@ApiResponse(responseCode = "400", description = "Alias inválido",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "El enlace expiró o no existe",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Alias en uso",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@PatchMapping(value = "/links/{alias:[A-Za-z0-9-]{1,32}}", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ShortLinkResponse changeAlias(Authentication authentication, @PathVariable String alias,
			@RequestBody ChangeAliasRequest request) {
		currentUser.of(authentication);
		return personalLinkService.rename(authentication.getName(), alias, request.alias());
	}

	@Operation(summary = "Cuánto le queda a la sesión; la de una computadora vence sola")
	@ApiResponse(responseCode = "200", description = "Duración de la sesión")
	@ApiResponse(responseCode = "401", description = "Sin sesión, o la sesión ya venció")
	@GetMapping(value = "/session", produces = MediaType.APPLICATION_JSON_VALUE)
	public SessionResponse session(HttpSession session) {
		return SessionResponse.of(desktopSession.expiresAt(session).orElse(null), desktopSession.now(), desktopSession.getWarning());
	}

	@Operation(summary = "Extiende la sesión de computadora app.session.desktop-ttl desde ahora")
	@ApiResponse(responseCode = "200", description = "Sesión extendida (sin límite, no cambia nada)")
	@ApiResponse(responseCode = "401", description = "Sin sesión, o la sesión ya venció")
	@PostMapping(value = "/session/extend", produces = MediaType.APPLICATION_JSON_VALUE)
	public SessionResponse extendSession(HttpSession session) {
		return SessionResponse.of(desktopSession.extend(session).orElse(null), desktopSession.now(), desktopSession.getWarning());
	}

	private AccountResponse response(AppUser user) {
		return AccountResponse.from(user, personalLinkService.namespaceUrl(user.getUsername()), desktopSession.now());
	}
}
