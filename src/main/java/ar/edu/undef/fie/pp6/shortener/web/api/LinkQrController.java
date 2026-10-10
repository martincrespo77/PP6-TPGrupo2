package ar.edu.undef.fie.pp6.shortener.web.api;

import ar.edu.undef.fie.pp6.shortener.application.LinkQrService;
import ar.edu.undef.fie.pp6.shortener.application.LinkQrService.LinkQr;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Enlaces")
@RestController
@RequestMapping("/api/v1/links")
public class LinkQrController {

	private final LinkQrService linkQrService;

	public LinkQrController(LinkQrService linkQrService) {
		this.linkQrService = linkQrService;
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
			@RequestParam(defaultValue = "" + LinkQrService.DEFAULT_SIZE) int size,
			@Parameter(description = "true para descargar como {alias}.png")
			@RequestParam(defaultValue = "false") boolean download) {
		LinkQr qr = linkQrService.qrFor(alias, size);
		HttpHeaders headers = new HttpHeaders();
		if (download) {
			headers.setContentDisposition(ContentDisposition.attachment().filename(qr.alias() + ".png").build());
		}
		return ResponseEntity.ok()
				.headers(headers)
				.contentType(MediaType.IMAGE_PNG)
				.cacheControl(CacheControl.noStore())
				.body(qr.png());
	}
}
