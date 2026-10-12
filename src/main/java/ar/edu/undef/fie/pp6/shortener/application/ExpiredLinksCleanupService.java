package ar.edu.undef.fie.pp6.shortener.application;

import ar.edu.undef.fie.pp6.shortener.domain.port.ShortLinkRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borra físicamente los enlaces vencidos (D5). Solo libera espacio: que un vencido no redirija ya lo
 * garantiza la lectura (I1), corra o no esta limpieza.
 */
@Service
public class ExpiredLinksCleanupService {

	private static final Logger log = LoggerFactory.getLogger(ExpiredLinksCleanupService.class);

	private final ShortLinkRepository repository;
	private final Clock clock;

	public ExpiredLinksCleanupService(ShortLinkRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/** @return cantidad de enlaces borrados */
	@Transactional
	public int purgeExpired() {
		int deleted = repository.deleteExpiredBefore(clock.instant());
		log.info("Limpieza de vencidos: {} enlace(s) borrado(s)", deleted);
		return deleted;
	}
}
