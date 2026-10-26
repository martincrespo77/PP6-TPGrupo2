package app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.exception.LoginRequestNotFoundException;
import app.mappings.LoginRequest;
import app.support.IntegrationTest;
import app.support.MutableClock;
import app.support.TestBeans;
import app.support.TestDb;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Ingreso por QR: pedido, aprobación y consumo")
@IntegrationTest
class LoginRequestServiceTest {

	private static final Instant NOW = TestBeans.START;

	@Autowired private LoginRequestService service;
	@Autowired private MutableClock clock;
	@Autowired private TestDb db;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		db.storeUser("google-ana", "ana", NOW);
	}

	@AfterEach
	void cleanUp() {
		db.deleteAll();
	}

	@Test
	void newRequestIsPendingForOneMinuteWithAnUnguessableCode() {
		LoginRequest request = service.create();

		assertThat(request.getCode()).matches("[A-Za-z0-9_-]{43}");
		assertThat(service.create().getCode()).isNotEqualTo(request.getCode());
		assertThat(service.poll(request.getCode()))
				.isEqualTo(new LoginRequestService.Poll(LoginRequestService.State.PENDING, null, NOW.plus(Duration.ofMinutes(1))));
	}

	@Test
	void discardedCodeCanNoLongerBeApproved() {
		String old = service.create().getCode();
		String current = service.create().getCode();

		service.discard(old);

		assertThat(service.poll(old).state()).isEqualTo(LoginRequestService.State.EXPIRED);
		assertThatThrownBy(() -> service.approve(old, "google-ana")).isInstanceOf(LoginRequestNotFoundException.class);
		assertThat(service.poll(current).state()).isEqualTo(LoginRequestService.State.PENDING);
	}

	@Test
	void qrPointsToTheApprovalPage() {
		assertThat(service.approvalUrl("abc")).isEqualTo("https://sho.rt/l/abc");
	}

	@Test
	void approvedRequestIsConsumedOnlyOnce() {
		String code = service.create().getCode();

		service.approve(code, "google-ana");

		LoginRequestService.Poll first = service.poll(code);
		assertThat(first.state()).isEqualTo(LoginRequestService.State.APPROVED);
		assertThat(first.googleSubject()).isEqualTo("google-ana");
		assertThat(service.poll(code).state()).isEqualTo(LoginRequestService.State.EXPIRED);
	}

	@Test
	void cannotBeApprovedTwice() {
		String code = service.create().getCode();
		service.approve(code, "google-ana");

		assertThatThrownBy(() -> service.approve(code, "google-ana")).isInstanceOf(LoginRequestNotFoundException.class);
	}

	@Test
	void expiredRequestCannotBeApprovedNorPolled() {
		String code = service.create().getCode();
		clock.advance(Duration.ofMinutes(2));

		assertThat(service.poll(code).state()).isEqualTo(LoginRequestService.State.EXPIRED);
		assertThatThrownBy(() -> service.approve(code, "google-ana")).isInstanceOf(LoginRequestNotFoundException.class);
	}

	@Test
	void approvalGivesTheComputerAFreshWindowToConsumeIt() {
		String code = service.create().getCode();
		clock.advance(Duration.ofSeconds(55));
		service.approve(code, "google-ana");
		clock.advance(Duration.ofSeconds(30));

		assertThat(service.poll(code).state()).isEqualTo(LoginRequestService.State.APPROVED);
	}

	@Test
	void unknownCodesAreExpired() {
		assertThat(service.poll("no-existe").state()).isEqualTo(LoginRequestService.State.EXPIRED);
		assertThat(service.poll(null).state()).isEqualTo(LoginRequestService.State.EXPIRED);
		assertThatThrownBy(() -> service.approve("no-existe", "google-ana")).isInstanceOf(LoginRequestNotFoundException.class);
	}

	@Test
	void purgeDeletesExpiredRequests() {
		String expired = service.create().getCode();
		clock.advance(Duration.ofMinutes(3));
		String live = service.create().getCode();

		assertThat(service.purgeExpired()).isEqualTo(1);
		assertThat(service.poll(expired).state()).isEqualTo(LoginRequestService.State.EXPIRED);
		assertThat(service.poll(live).state()).isEqualTo(LoginRequestService.State.PENDING);
	}
}
