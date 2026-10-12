package app.controller;

import app.dto.ShortLinkResponse;
import app.dto.ShortenRequest;
import app.service.LinkService;
import app.service.QrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Enlaces", description = "Creación de enlaces cortos")
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

	@Autowired
	private LinkService linkService;

	@Autowired
	private QrService qrService;

	@Operation(summary = "Acorta una URL; el enlace vence a los 60 minutos")
	@ApiResponse(responseCode = "201", description = "Enlace creado")
	@ApiResponse(responseCode = "400", description = "URL inválida",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "No se pudo asignar un alias; reintentar",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ShortLinkResponse> shorten(@RequestBody ShortenRequest request) {
		ShortLinkResponse response = linkService.shorten(request.url());
		return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
	}

	@Operation(summary = "PNG con el QR de la URL corta de un enlace vigente")
	@ApiResponse(responseCode = "200", description = "Imagen PNG",
			content = @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary")))
	@ApiResponse(responseCode = "400", description = "size fuera de 128..1024",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "El enlace expiró o no existe",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping(value = "/{alias:[A-Za-z0-9]{1,16}}/qr", produces = MediaType.IMAGE_PNG_VALUE)
	public ResponseEntity<byte[]> qr(
			@PathVariable String alias,
			@Parameter(description = "Lado en píxeles, entre 128 y 1024")
			@RequestParam(defaultValue = "" + QrService.DEFAULT_SIZE) int size,
			@Parameter(description = "true para descargar como {alias}.png")
			@RequestParam(defaultValue = "false") boolean download) {
		byte[] png = qrService.qrFor(alias, size);
		HttpHeaders headers = new HttpHeaders();
		if (download) {
			String fileName = alias.toLowerCase(Locale.ROOT) + ".png";
			headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
		}
		return ResponseEntity.ok()
				.headers(headers)
				.contentType(MediaType.IMAGE_PNG)
				.cacheControl(CacheControl.noStore())
				.body(png);
	}
}
