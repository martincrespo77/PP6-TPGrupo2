package ar.edu.undef.fie.pp6.shortener.web.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Sin anotaciones de Bean Validation: todas las reglas y sus mensajes viven en UrlValidator. */
public record ShortenRequest(
		@Schema(description = "URL a acortar (http o https, hasta 2048 caracteres)",
				example = "https://drive.google.com/drive/folders/1a2b3c4d5e6f7g8h9?usp=sharing")
		String url) {
}
