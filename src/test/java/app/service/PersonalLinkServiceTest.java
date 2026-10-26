package app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.dto.ShortLinkResponse;
import app.exception.InvalidAliasException;
import app.exception.InvalidUrlException;
import app.exception.LinkNotFoundException;
import app.exception.PersonalAliasTakenException;
import app.exception.UsernameNotChosenException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Enlaces personales /{usuario}/{alias}")
@IntegrationTest
class PersonalLinkServiceTest {

	private static final Instant NOW = TestBeans.START;
	private static final Instant EXPIRES = NOW.plus(Duration.ofMinutes(60));
	private static final String TARGET = "https://ejemplo.com/destino";

	@Autowired private PersonalLinkService service;
	@Autowired private UserService users;
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
	void createsALinkInTheUserNamespaceThatExpiresSixtyMinutesLater() {
		ShortLinkResponse link = service.create("google-ana", TARGET, "Facultad");

		assertThat(link.alias()).isEqualTo("facultad");
		assertThat(link.shortUrl()).isEqualTo("https://sho.rt/ana/facultad");
		assertThat(link.qrUrl()).isEqualTo("https://sho.rt/api/v1/users/ana/links/facultad/qr");
		assertThat(link.expiresAt()).isEqualTo(EXPIRES);
		assertThat(link.secondsRemaining()).isEqualTo(3600);
		assertThat(service.resolve("ana", "facultad")).contains(TARGET);
	}

	@Test
	void resolvingIgnoresCase() {
		service.create("google-ana", TARGET, "facultad");

		assertThat(service.resolve("ANA", "Facultad")).contains(TARGET);
	}

	@Test
	void aLiveAliasCannotBeReusedByItsOwner() {
		service.create("google-ana", TARGET, "facultad");

		assertThatThrownBy(() -> service.create("google-ana", "https://otro.com", "facultad"))
				.isInstanceOf(PersonalAliasTakenException.class);
		assertThat(service.resolve("ana", "facultad")).contains(TARGET);
	}

	@Test
	void anExpiredAliasIsReassigned() {
		db.storePersonalLink("ana", "facultad", "https://viejo.com", NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)));

		service.create("google-ana", "https://nuevo.com", "facultad");

		assertThat(service.resolve("ana", "facultad")).contains("https://nuevo.com");
		assertThat(db.countPersonalLinks()).isEqualTo(1);
	}

	@Test
	void differentUsersCanUseTheSameAlias() {
		db.storeUser("google-beto", "beto", NOW);

		service.create("google-ana", "https://ana.com", "cv");
		service.create("google-beto", "https://beto.com", "cv");

		assertThat(service.resolve("ana", "cv")).contains("https://ana.com");
		assertThat(service.resolve("beto", "cv")).contains("https://beto.com");
	}

	@Test
	void expiresLikePublicLinks() {
		service.create("google-ana", TARGET, "facultad");

		clock.set(EXPIRES.minusMillis(1));
		assertThat(service.resolve("ana", "facultad")).contains(TARGET);
		clock.set(EXPIRES);
		assertThat(service.resolve("ana", "facultad")).isEmpty();
	}

	@Test
	void linksFollowTheUserWhenTheNameChanges() {
		service.create("google-ana", TARGET, "facultad");

		users.chooseUsername("google-ana", "ana-maria");

		assertThat(service.resolve("ana-maria", "facultad")).contains(TARGET);
		assertThat(service.resolve("ana", "facultad")).isEmpty();
	}

	@Test
	void aUserWithTheRandomNameCannotCreateLinks() {
		users.registerIfNew("google-nuevo", null);

		assertThatThrownBy(() -> service.create("google-nuevo", TARGET, "facultad"))
				.isInstanceOf(UsernameNotChosenException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "con espacio", "con/barra", "punto.final", "abcdefghijklmnopqrstuvwxyz1234567" })
	void rejectsInvalidAliases(String alias) {
		assertThatThrownBy(() -> service.create("google-ana", TARGET, alias)).isInstanceOf(InvalidAliasException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void withoutAliasGeneratesARandomOne(String alias) {
		ShortLinkResponse link = service.create("google-ana", TARGET, alias);

		assertThat(link.alias()).hasSize(PersonalLinkService.RANDOM_LENGTH)
				.matches("[" + PersonalLinkService.RANDOM_ALPHABET + "]+");
		assertThat(link.shortUrl()).isEqualTo("https://sho.rt/ana/" + link.alias());
		assertThat(service.resolve("ana", link.alias())).contains(TARGET);
	}

	@Test
	void nullAliasAlsoGeneratesOne() {
		ShortLinkResponse first = service.create("google-ana", TARGET, null);
		ShortLinkResponse second = service.create("google-ana", TARGET, null);

		assertThat(first.alias()).isNotEqualTo(second.alias());
		assertThat(db.countPersonalLinks()).isEqualTo(2);
	}

	@Test
	void renameKeepsTheDestinationAndTheExpiration() {
		ShortLinkResponse created = service.create("google-ana", TARGET, null);
		clock.set(NOW.plus(Duration.ofMinutes(10)));

		ShortLinkResponse renamed = service.rename("google-ana", created.alias(), "Facultad");

		assertThat(renamed.alias()).isEqualTo("facultad");
		assertThat(renamed.shortUrl()).isEqualTo("https://sho.rt/ana/facultad");
		assertThat(renamed.expiresAt()).isEqualTo(EXPIRES);
		assertThat(renamed.secondsRemaining()).isEqualTo(3000);
		assertThat(service.resolve("ana", "facultad")).contains(TARGET);
		assertThat(service.resolve("ana", created.alias())).isEmpty();
		assertThat(db.countPersonalLinks()).isEqualTo(1);
	}

	@Test
	void renameToTheSameAliasChangesNothing() {
		service.create("google-ana", TARGET, "facultad");

		assertThat(service.rename("google-ana", "facultad", "facultad").alias()).isEqualTo("facultad");
		assertThat(service.resolve("ana", "facultad")).contains(TARGET);
	}

	@Test
	void renameToALiveAliasFails() {
		service.create("google-ana", TARGET, "facultad");
		service.create("google-ana", "https://otro.com", "cv");

		assertThatThrownBy(() -> service.rename("google-ana", "cv", "facultad")).isInstanceOf(PersonalAliasTakenException.class);
		assertThat(service.resolve("ana", "cv")).contains("https://otro.com");
	}

	@Test
	void renameCanTakeTheAliasOfAnExpiredLink() {
		db.storePersonalLink("ana", "facultad", "https://viejo.com", NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)));
		service.create("google-ana", TARGET, "cv");

		service.rename("google-ana", "cv", "facultad");

		assertThat(service.resolve("ana", "facultad")).contains(TARGET);
		assertThat(db.countPersonalLinks()).isEqualTo(1);
	}

	@Test
	void renameRejectsUnknownExpiredOrOtherUsersLinks() {
		db.storeUser("google-beto", "beto", NOW);
		service.create("google-beto", TARGET, "cv");
		db.storePersonalLink("ana", "viejo", TARGET, NOW.minus(Duration.ofHours(2)), NOW);

		assertThatThrownBy(() -> service.rename("google-ana", "nada", "nuevo")).isInstanceOf(LinkNotFoundException.class);
		assertThatThrownBy(() -> service.rename("google-ana", "viejo", "nuevo")).isInstanceOf(LinkNotFoundException.class);
		assertThatThrownBy(() -> service.rename("google-ana", "cv", "nuevo")).isInstanceOf(LinkNotFoundException.class);
		assertThat(service.resolve("beto", "cv")).contains(TARGET);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "con espacio", "punto.final" })
	void renameRejectsInvalidAliases(String alias) {
		service.create("google-ana", TARGET, "facultad");

		assertThatThrownBy(() -> service.rename("google-ana", "facultad", alias)).isInstanceOf(InvalidAliasException.class);
	}

	@Test
	void rejectsInvalidUrlsAndLinksToThemselves() {
		assertThatThrownBy(() -> service.create("google-ana", "ftp://ejemplo.com", "a")).isInstanceOf(InvalidUrlException.class);
		assertThatThrownBy(() -> service.create("google-ana", "https://sho.rt/ana/a", "a")).isInstanceOf(InvalidUrlException.class);
		assertThat(db.countPersonalLinks()).isZero();
	}

	@Test
	void purgeDeletesOnlyExpiredLinks() {
		db.storePersonalLink("ana", "viejo", TARGET, NOW.minus(Duration.ofHours(2)), NOW);
		db.storePersonalLink("ana", "vivo", TARGET, NOW, NOW.plusMillis(1));

		assertThat(service.purgeExpired()).isEqualTo(1);
		assertThat(service.resolve("ana", "vivo")).contains(TARGET);
	}
}
