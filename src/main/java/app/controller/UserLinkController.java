package app.controller;

import app.service.QrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** QR público de los enlaces personales, como el de los enlaces públicos (§10.3). */
@Tag(name = "Enlaces personales", description = "QR de /{usuario}/{alias}")
@RestController
@RequestMapping("/api/v1/users")
public class UserLinkController {

	@Autowired
	private QrService qrService;

	@Operation(summary = "PNG con el QR de un enlace personal vigente")
	@ApiResponse(responseCode = "200", description = "Imagen PNG",
			content = @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary")))
	@ApiResponse(responseCode = "404", description = "El enlace expiró o no existe",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping(value = "/{username:[A-Za-z0-9-]{3,20}}/links/{alias:[A-Za-z0-9-]{1,32}}/qr", produces = MediaType.IMAGE_PNG_VALUE)
	public ResponseEntity<byte[]> qr(
			@PathVariable String username,
			@PathVariable String alias,
			@Parameter(description = "Lado en píxeles, entre 128 y 1024")
			@RequestParam(defaultValue = "" + QrService.DEFAULT_SIZE) int size,
			@Parameter(description = "true para descargar como {usuario}-{alias}.png")
			@RequestParam(defaultValue = "false") boolean download) {
		byte[] png = qrService.qrForPersonal(username, alias, size);
		HttpHeaders headers = new HttpHeaders();
		if (download) {
			String fileName = (username + "-" + alias).toLowerCase(Locale.ROOT) + ".png";
			headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
		}
		return ResponseEntity.ok()
				.headers(headers)
				.contentType(MediaType.IMAGE_PNG)
				.cacheControl(CacheControl.noStore())
				.body(png);
	}
}
