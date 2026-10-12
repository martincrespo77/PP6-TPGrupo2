package app.dto;

import app.mappings.ShortLink;
import java.time.Duration;
import java.time.Instant;

/** Respuesta de creación (§10.1). Nunca se serializa la entidad (D28). */
public record ShortLinkResponse(
		String alias,
		String shortUrl,
		String originalUrl,
		Instant createdAt,
		Instant expiresAt,
		long secondsRemaining,
		String qrUrl) {

	/** Redondea hacia arriba: con redondeo hacia abajo mostraría 0 segundos con el enlace todavía vigente. */
	public static ShortLinkResponse from(ShortLink link, String shortUrl, String qrUrl, Instant now) {
		long millis = Duration.between(now, link.getExpiresAt()).toMillis();
		long remaining = Math.max(0, Math.ceilDiv(millis, 1000));
		return new ShortLinkResponse(
				link.getAlias(),
				shortUrl,
				link.getOriginalUrl(),
				link.getCreatedAt(),
				link.getExpiresAt(),
				remaining,
				qrUrl);
	}
}
