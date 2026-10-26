package app.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Sin Bean Validation: las reglas y sus mensajes viven en UserService. */
public record ChooseUsernameRequest(
		@Schema(description = "3 a 20 caracteres: minúsculas, números y guiones", example = "martin")
		String username) {
}
