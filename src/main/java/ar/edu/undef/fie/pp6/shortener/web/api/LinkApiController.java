package ar.edu.undef.fie.pp6.shortener.web.api;

import ar.edu.undef.fie.pp6.shortener.application.LinkUrls;
import ar.edu.undef.fie.pp6.shortener.application.ShortenLinkService;
import ar.edu.undef.fie.pp6.shortener.domain.model.ShortLink;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Clock;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Enlaces", description = "Creación de enlaces cortos")
@RestController
@RequestMapping("/api/v1/links")
public class LinkApiController {

	private final ShortenLinkService shortenLinkService;
	private final LinkUrls linkUrls;
	private final Clock clock;

	public LinkApiController(ShortenLinkService shortenLinkService, LinkUrls linkUrls, Clock clock) {
		this.shortenLinkService = shortenLinkService;
		this.linkUrls = linkUrls;
		this.clock = clock;
	}

	@Operation(summary = "Acorta una URL; el enlace vence a los 60 minutos")
	@ApiResponse(responseCode = "201", description = "Enlace creado")
	@ApiResponse(responseCode = "400", description = "URL inválida",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "No se pudo asignar un alias; reintentar",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ShortLinkResponse> shorten(@RequestBody ShortenRequest request) {
		ShortLink link = shortenLinkService.shorten(request.url());
		ShortLinkResponse response = ShortLinkResponse.from(link, linkUrls, clock.instant());
		return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
	}
}
