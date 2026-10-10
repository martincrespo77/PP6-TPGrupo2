package ar.edu.undef.fie.pp6.shortener.domain.port;

import java.time.Instant;

/** Calcula cuándo vence un enlace creado en un instante dado. */
public interface ExpirationPolicy {

	Instant expirationFor(Instant createdAt);
}
