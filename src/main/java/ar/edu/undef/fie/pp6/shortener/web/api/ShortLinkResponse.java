package ar.edu.undef.fie.pp6.shortener.web.api;

import ar.edu.undef.fie.pp6.shortener.application.LinkUrls;
import ar.edu.undef.fie.pp6.shortener.domain.model.ShortLink;
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

	static ShortLinkResponse from(ShortLink link, LinkUrls urls, Instant now) {
		long remaining = Math.max(0, Duration.between(now, link.getExpiresAt()).toSeconds());
		return new ShortLinkResponse(
				link.getAlias(),
				urls.shortUrl(link.getAlias()),
				link.getOriginalUrl(),
				link.getCreatedAt(),
				link.getExpiresAt(),
				remaining,
				urls.qrUrl(link.getAlias()));
	}
}
