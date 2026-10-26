package app.dto;

import app.mappings.PersonalLink;
import app.mappings.ShortLink;
import java.time.Duration;
import java.time.Instant;

/** Respuesta de creación (§10.1), de enlaces públicos y personales. Nunca se serializa la entidad (D28). */
public record ShortLinkResponse(
		String alias,
		String shortUrl,
		String originalUrl,
		Instant createdAt,
		Instant expiresAt,
		long secondsRemaining,
		String qrUrl) {

	public static ShortLinkResponse from(ShortLink link, String shortUrl, String qrUrl, Instant now) {
		return of(link.getAlias(), shortUrl, link.getOriginalUrl(), link.getCreatedAt(), link.getExpiresAt(), qrUrl, now);
	}

	public static ShortLinkResponse from(PersonalLink link, String shortUrl, String qrUrl, Instant now) {
		return of(link.getAlias(), shortUrl, link.getOriginalUrl(), link.getCreatedAt(), link.getExpiresAt(), qrUrl, now);
	}

	/** Redondea hacia arriba: con redondeo hacia abajo mostraría 0 segundos con el enlace todavía vigente. */
	private static ShortLinkResponse of(String alias, String shortUrl, String originalUrl, Instant createdAt,
			Instant expiresAt, String qrUrl, Instant now) {
		long millis = Duration.between(now, expiresAt).toMillis();
		long remaining = Math.max(0, Math.ceilDiv(millis, 1000));
		return new ShortLinkResponse(alias, shortUrl, originalUrl, createdAt, expiresAt, remaining, qrUrl);
	}
}
