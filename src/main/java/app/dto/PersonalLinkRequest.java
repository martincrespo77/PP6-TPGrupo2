package app.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Sin Bean Validation: las reglas y sus mensajes viven en UrlValidator y PersonalLinkService. */
public record PersonalLinkRequest(
		@Schema(description = "URL a acortar (http o https, hasta 2048 caracteres)", example = "https://www.undef.edu.ar/")
		String url,
		@Schema(description = "Alias dentro de tu espacio: 1 a 32 letras, números o guiones. Vacío: se genera uno aleatorio",
				example = "facultad")
		String alias) {
}
