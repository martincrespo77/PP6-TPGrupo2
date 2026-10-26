package app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.exception.InvalidUsernameException;
import app.exception.UserNotFoundException;
import app.exception.UsernameUnavailableException;
import app.mappings.AppUser;
import app.support.IntegrationTest;
import app.support.TestBeans;
import app.support.TestDb;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Usuarios: alta con Google y nombre de usuario")
@IntegrationTest
class UserServiceTest {

	@Autowired private UserService service;
	@Autowired private TestDb db;

	@AfterEach
	void cleanUp() {
		db.deleteAll();
	}

	@Test
	void firstLoginRegistersTheUserWithARandomNameThatStillHasToBeChosen() {
		AppUser user = service.registerIfNew("google-1", "ana@gmail.com");

		assertThat(user.getUsername()).matches("usuario-[a-z2-9]{8}");
		assertThat(user.isUsernameChosen()).isFalse();
		assertThat(user.getEmail()).isEqualTo("ana@gmail.com");
		assertThat(user.getCreatedAt()).isEqualTo(TestBeans.START);
	}

	@Test
	void laterLoginsReturnTheSameUserAndKeepTheChosenName() {
		service.registerIfNew("google-1", "ana@gmail.com");
		service.chooseUsername("google-1", "ana");

		AppUser again = service.registerIfNew("google-1", null);

		assertThat(again.getUsername()).isEqualTo("ana");
		assertThat(again.getEmail()).as("sin correo en la sesión no se borra").isEqualTo("ana@gmail.com");
	}

	@Test
	void randomNamesAreDifferentForEachUser() {
		assertThat(service.registerIfNew("google-1", null).getUsername())
				.isNotEqualTo(service.registerIfNew("google-2", null).getUsername());
	}

	@Test
	void choosesANameAndStoresItInLowercase() {
		service.registerIfNew("google-1", null);

		AppUser user = service.chooseUsername("google-1", "  Martin-77 ");

		assertThat(user.getUsername()).isEqualTo("martin-77");
		assertThat(user.isUsernameChosen()).isTrue();
		assertThat(service.require("google-1").getUsername()).isEqualTo("martin-77");
	}

	@Test
	void canChangeTheChosenNameAndKeepTheSameOne() {
		service.registerIfNew("google-1", null);
		service.chooseUsername("google-1", "ana");

		assertThat(service.chooseUsername("google-1", "ana").getUsername()).isEqualTo("ana");
		assertThat(service.chooseUsername("google-1", "ana-maria").getUsername()).isEqualTo("ana-maria");
	}

	@Test
	void aNameTakenByAnotherUserIsRejected() {
		service.registerIfNew("google-1", null);
		service.registerIfNew("google-2", null);
		service.chooseUsername("google-1", "ana");

		assertThatThrownBy(() -> service.chooseUsername("google-2", "ANA")).isInstanceOf(UsernameUnavailableException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "ab", "abcdefghijklmnopqrstu", "-ana", "ana-", "ana maria", "ana_maria", "ana.maria", "ñandu" })
	void rejectsNamesWithAnInvalidFormat(String name) {
		service.registerIfNew("google-1", null);

		assertThatThrownBy(() -> service.chooseUsername("google-1", name)).isInstanceOf(InvalidUsernameException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "api", "login", "cuenta", "jpql-console", "Swagger-UI" })
	void rejectsReservedNames(String name) {
		service.registerIfNew("google-1", null);

		assertThatThrownBy(() -> service.chooseUsername("google-1", name))
				.isInstanceOf(InvalidUsernameException.class)
				.hasMessageContaining("reservado");
	}

	@Test
	void theRandomNameCannotBeKeptSoItHasToBeChanged() {
		String random = service.registerIfNew("google-1", null).getUsername();

		assertThatThrownBy(() -> service.chooseUsername("google-1", random))
				.isInstanceOf(InvalidUsernameException.class)
				.hasMessageContaining("usuario-");
	}

	@Test
	void unknownUserIsRejected() {
		assertThatThrownBy(() -> service.require("nadie")).isInstanceOf(UserNotFoundException.class);
	}
}
