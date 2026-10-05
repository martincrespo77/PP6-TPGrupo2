package com.pp6.shortener.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
		@NotBlank String baseUrl,
		@NotNull @Valid Link link,
		@NotNull @Valid Alias alias) {

	public record Link(@NotNull Duration ttl) {
	}

	public record Alias(
			@Min(1) @Max(16) int length,
			@NotBlank String alphabet) {
	}
}
