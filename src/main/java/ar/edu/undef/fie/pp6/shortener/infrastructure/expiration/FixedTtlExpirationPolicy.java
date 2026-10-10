package ar.edu.undef.fie.pp6.shortener.infrastructure.expiration;

import ar.edu.undef.fie.pp6.shortener.config.AppProperties;
import ar.edu.undef.fie.pp6.shortener.domain.port.ExpirationPolicy;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Vencimiento = creación + TTL fijo (app.link.ttl, 60 minutos por defecto). */
@Component
public class FixedTtlExpirationPolicy implements ExpirationPolicy {

	private final Duration ttl;

	@Autowired
	public FixedTtlExpirationPolicy(AppProperties properties) {
		this(properties.link().ttl());
	}

	FixedTtlExpirationPolicy(Duration ttl) {
		if (ttl.isZero() || ttl.isNegative()) {
			throw new IllegalArgumentException("app.link.ttl debe ser positivo: " + ttl);
		}
		this.ttl = ttl;
	}

	@Override
	public Instant expirationFor(Instant createdAt) {
		return createdAt.plus(ttl);
	}
}
