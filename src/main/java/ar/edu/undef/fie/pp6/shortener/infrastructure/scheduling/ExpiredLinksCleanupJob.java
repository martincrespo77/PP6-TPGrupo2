package ar.edu.undef.fie.pp6.shortener.infrastructure.scheduling;

import ar.edu.undef.fie.pp6.shortener.application.ExpiredLinksCleanupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dispara la limpieza según app.cleanup.cron (por defecto, todos los días a las 3:00 hora del servidor). */
@Component
public class ExpiredLinksCleanupJob {

	private final ExpiredLinksCleanupService cleanupService;

	public ExpiredLinksCleanupJob(ExpiredLinksCleanupService cleanupService) {
		this.cleanupService = cleanupService;
	}

	@Scheduled(cron = "${app.cleanup.cron}")
	public void run() {
		cleanupService.purgeExpired();
	}
}
