package app.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Sin Bean Validation: las reglas y sus mensajes viven en PersonalLinkService. */
public record ChangeAliasRequest(
		@Schema(description = "Alias nuevo: 1 a 32 letras, números o guiones", example = "facultad")
		String alias) {
}
