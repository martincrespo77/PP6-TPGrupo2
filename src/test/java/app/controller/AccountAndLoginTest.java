package app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.support.IntegrationTest;
import app.support.MutableClock;
import app.support.QrDecoder;
import app.support.TestBeans;
import app.support.TestDb;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AccountAndLoginTest {

	@Autowired private MockMvc mvc;
	@Autowired private MutableClock clock;
	@Autowired private TestDb db;

	private static OidcLoginRequestPostProcessor google(String subject) {
		return oidcLogin().idToken(token -> token.subject(subject).claim("email", subject + "@gmail.com"));
	}

	@BeforeEach
	void setUp() {
		clock.set(TestBeans.START);
	}

	@AfterEach
	void cleanUp() {
		db.deleteAll();
	}

	@Nested
	@DisplayName("Ingreso de la computadora escaneando un QR con el celular")
	class QrLogin {

		@BeforeEach
		void phoneUserExists() {
			db.storeUser("google-ana", "ana", TestBeans.START);
		}

		@Test
		void phoneApprovesTheQrAndTheComputerSessionIsSignedIn() throws Exception {
			MockHttpSession computer = new MockHttpSession();
			mvc.perform(post("/api/v1/login-requests").session(computer).with(csrf()))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.status").value("PENDING"))
					.andExpect(jsonPath("$.secondsRemaining").value(60));
			String code = (String) computer.getAttribute(LoginRequestController.SESSION_CODE);

			byte[] png = mvc.perform(get("/api/v1/login-requests/current/qr").session(computer))
					.andExpect(status().isOk())
					.andExpect(content().contentType(MediaType.IMAGE_PNG))
					.andReturn().getResponse().getContentAsByteArray();
			assertThat(QrDecoder.decode(png)).isEqualTo("https://sho.rt/l/" + code);

			mvc.perform(get("/api/v1/login-requests/current").session(computer))
					.andExpect(jsonPath("$.status").value("PENDING"));
			mvc.perform(get("/api/v1/me").session(computer)).andExpect(status().isUnauthorized());

			mvc.perform(post("/api/v1/login-requests/" + code + "/approve").with(google("google-ana")).with(csrf()))
					.andExpect(status().isNoContent());

			mvc.perform(get("/api/v1/login-requests/current").session(computer))
					.andExpect(jsonPath("$.status").value("APPROVED"));
			mvc.perform(get("/api/v1/me").session(computer))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.username").value("ana"));
		}

		@Test
		void anotherSessionThatSawTheQrCannotUseIt() throws Exception {
			MockHttpSession computer = new MockHttpSession();
			mvc.perform(post("/api/v1/login-requests").session(computer).with(csrf())).andExpect(status().isCreated());
			String code = (String) computer.getAttribute(LoginRequestController.SESSION_CODE);
			mvc.perform(post("/api/v1/login-requests/" + code + "/approve").with(google("google-ana")).with(csrf()))
					.andExpect(status().isNoContent());

			MockHttpSession intruder = new MockHttpSession();
			mvc.perform(get("/api/v1/login-requests/current").session(intruder))
					.andExpect(jsonPath("$.status").value("EXPIRED"));
			mvc.perform(get("/api/v1/me").session(intruder)).andExpect(status().isUnauthorized());
		}

		@Test
		void codeExpiresIfNobodyApprovesIt() throws Exception {
			MockHttpSession computer = new MockHttpSession();
			mvc.perform(post("/api/v1/login-requests").session(computer).with(csrf())).andExpect(status().isCreated());
			String code = (String) computer.getAttribute(LoginRequestController.SESSION_CODE);
			clock.advance(Duration.ofMinutes(2));

			mvc.perform(get("/api/v1/login-requests/current").session(computer))
					.andExpect(jsonPath("$.status").value("EXPIRED"));
			mvc.perform(post("/api/v1/login-requests/" + code + "/approve").with(google("google-ana")).with(csrf()))
					.andExpect(status().isNotFound())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}

		@Test
		void approvingRequiresASignedInPhoneAndTheCsrfToken() throws Exception {
			String code = "a".repeat(43);

			mvc.perform(post("/api/v1/login-requests/" + code + "/approve").with(csrf())).andExpect(status().isUnauthorized());
			mvc.perform(post("/api/v1/login-requests/" + code + "/approve").with(google("google-ana")))
					.andExpect(status().isForbidden());
		}

		@Test
		void creatingACodeRequiresTheCsrfToken() throws Exception {
			mvc.perform(post("/api/v1/login-requests")).andExpect(status().isForbidden());
		}

		@Test
		void qrPageOnThePhoneSendsToGoogleLoginFirst() throws Exception {
			String path = "/l/" + "b".repeat(43);

			mvc.perform(get(path))
					.andExpect(status().isFound())
					.andExpect(header().string("Location", endsWith("/oauth2/authorization/google")));
			mvc.perform(get(path).with(google("google-ana")))
					.andExpect(status().isOk())
					.andExpect(forwardedUrl("/confirmar-ingreso.html"));
		}
	}

	@Nested
	@DisplayName("Cuenta: nombre de usuario y enlaces personales")
	class Account {

		@Test
		void withoutSessionTheApiAnswers401() throws Exception {
			mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
			mvc.perform(post("/api/v1/me/links").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andExpect(status().isUnauthorized());
		}

		@Test
		void firstGoogleLoginRegistersTheUserWithARandomName() throws Exception {
			mvc.perform(get("/api/v1/me").with(google("google-nuevo")))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.username", matchesPattern("usuario-[a-z2-9]{8}")))
					.andExpect(jsonPath("$.usernameChosen").value(false))
					.andExpect(jsonPath("$.email").value("google-nuevo@gmail.com"));
		}

		@Test
		void choosingTheNameRequiresTheCsrfTokenAndThenEnablesTheNamespace() throws Exception {
			String body = "{\"username\":\"Ana\"}";
			mvc.perform(put("/api/v1/me/username").with(google("google-ana")).contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isForbidden());

			mvc.perform(put("/api/v1/me/username").with(google("google-ana")).with(csrf())
							.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.username").value("ana"))
					.andExpect(jsonPath("$.usernameChosen").value(true))
					.andExpect(jsonPath("$.namespaceUrl").value("https://sho.rt/ana/"));
		}

		@Test
		void invalidOrTakenNamesAreRejectedWithAProblemDetail() throws Exception {
			db.storeUser("google-beto", "beto", TestBeans.START);

			mvc.perform(put("/api/v1/me/username").with(google("google-ana")).with(csrf())
							.contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"a b\"}"))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
			mvc.perform(put("/api/v1/me/username").with(google("google-ana")).with(csrf())
							.contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"beto\"}"))
					.andExpect(status().isConflict());
		}

		@Test
		void personalLinkCanBeCreatedRedirectsAndHasAQr() throws Exception {
			mvc.perform(post("/api/v1/me/links").with(google("google-ana")).with(csrf())
							.contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://undef.edu.ar\",\"alias\":\"facultad\"}"))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.detail").value("Antes de crear enlaces elegí tu nombre de usuario"));

			mvc.perform(put("/api/v1/me/username").with(google("google-ana")).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"ana\"}"));
			mvc.perform(post("/api/v1/me/links").with(google("google-ana")).with(csrf())
							.contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://undef.edu.ar\",\"alias\":\"facultad\"}"))
					.andExpect(status().isCreated())
					.andExpect(header().string("Location", "https://sho.rt/ana/facultad"))
					.andExpect(jsonPath("$.shortUrl").value("https://sho.rt/ana/facultad"))
					.andExpect(jsonPath("$.secondsRemaining").value(3600));

			mvc.perform(get("/ana/facultad"))
					.andExpect(status().isFound())
					.andExpect(header().string("Location", "https://undef.edu.ar"));
			byte[] png = mvc.perform(get("/api/v1/users/ana/links/facultad/qr"))
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsByteArray();
			assertThat(QrDecoder.decode(png)).isEqualTo("https://sho.rt/ana/facultad");
		}

		@Test
		void linkWithoutAliasGetsARandomOneThatCanBeRenamed() throws Exception {
			mvc.perform(put("/api/v1/me/username").with(google("google-ana")).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"ana\"}"));
			String body = mvc.perform(post("/api/v1/me/links").with(google("google-ana")).with(csrf())
							.contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://undef.edu.ar\",\"alias\":\"\"}"))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.alias", matchesPattern("[a-z2-9]{6}")))
					.andReturn().getResponse().getContentAsString();
			String random = body.replaceAll(".*\"alias\":\"([^\"]+)\".*", "$1");

			mvc.perform(patch("/api/v1/me/links/" + random).with(google("google-ana"))
							.contentType(MediaType.APPLICATION_JSON).content("{\"alias\":\"facultad\"}"))
					.andExpect(status().isForbidden());
			mvc.perform(patch("/api/v1/me/links/" + random).with(google("google-ana")).with(csrf())
							.contentType(MediaType.APPLICATION_JSON).content("{\"alias\":\"facultad\"}"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.shortUrl").value("https://sho.rt/ana/facultad"));

			mvc.perform(get("/ana/facultad"))
					.andExpect(status().isFound())
					.andExpect(header().string("Location", "https://undef.edu.ar"));
			mvc.perform(patch("/api/v1/me/links/" + random).with(google("google-ana")).with(csrf())
							.contentType(MediaType.APPLICATION_JSON).content("{\"alias\":\"otro\"}"))
					.andExpect(status().isNotFound());
		}

		@Test
		void unknownOrExpiredPersonalLinksShowTheSameNotAvailablePage() throws Exception {
			db.storeUser("google-ana", "ana", TestBeans.START);
			db.storePersonalLink("ana", "viejo", "https://viejo.com", TestBeans.START.minus(Duration.ofHours(2)),
					TestBeans.START.minus(Duration.ofHours(1)));

			String expired = mvc.perform(get("/ana/viejo")).andExpect(status().isNotFound())
					.andReturn().getResponse().getContentAsString();
			String missing = mvc.perform(get("/nadie/nada")).andExpect(status().isNotFound())
					.andReturn().getResponse().getContentAsString();
			assertThat(expired).contains("Este enlace expiró o no existe").isEqualTo(missing);
			mvc.perform(get("/api/v1/users/ana/links/viejo/qr")).andExpect(status().isNotFound());
		}

		@Test
		void reuseExistingRenewsTheNewestLiveLinkToTheSameUrl() throws Exception {
			db.storeUser("google-ana", "ana", TestBeans.START);
			db.storePersonalLink("ana", "facultad", "https://undef.edu.ar", TestBeans.START, TestBeans.START.plus(Duration.ofMinutes(60)));
			clock.advance(Duration.ofMinutes(30));

			mvc.perform(post("/api/v1/me/links").with(google("google-ana")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
							.content("{\"url\":\"https://undef.edu.ar\",\"alias\":\"\",\"reuseExisting\":true}"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.shortUrl").value("https://sho.rt/ana/facultad"))
					.andExpect(jsonPath("$.secondsRemaining").value(3600));
			mvc.perform(post("/api/v1/me/links").with(google("google-ana")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
							.content("{\"url\":\"https://otra.com\",\"alias\":\"\",\"reuseExisting\":true}"))
					.andExpect(status().isCreated());
			assertThat(db.countPersonalLinks()).isEqualTo(2);
		}

		@Test
		void logoutEndsTheSession() throws Exception {
			MockHttpSession session = new MockHttpSession();
			mvc.perform(get("/api/v1/me").session(session).with(google("google-ana"))).andExpect(status().isOk());

			mvc.perform(post("/api/v1/logout").session(session).with(csrf())).andExpect(status().isOk());

			assertThat(session.isInvalid()).isTrue();
		}
	}

	@Nested
	@DisplayName("Cambio de nombre: página /{usuario}, período de gracia y enlaces anteriores")
	class Rename {

		@BeforeEach
		void anaHasALink() {
			db.storeUser("google-ana", "ana", TestBeans.START);
			db.storePersonalLink("ana", "facultad", "https://undef.edu.ar", TestBeans.START, TestBeans.START.plus(Duration.ofMinutes(60)));
		}

		private void rename(String username) throws Exception {
			mvc.perform(put("/api/v1/me/username").with(google("google-ana")).with(csrf())
							.contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + username + "\"}"))
					.andExpect(status().isOk());
		}

		@Test
		void theUserAddressServesTheAccountPage() throws Exception {
			mvc.perform(get("/ana"))
					.andExpect(status().isOk())
					.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
					.andExpect(content().string(org.hamcrest.Matchers.containsString("/cuenta.js")));
		}

		@Test
		void duringTheGracePeriodOldLinksShowTheNewAddressAndStillWork() throws Exception {
			rename("ana-maria");

			mvc.perform(get("/api/v1/me").with(google("google-ana")))
					.andExpect(jsonPath("$.username").value("ana-maria"))
					.andExpect(jsonPath("$.previousUsername").value("ana"))
					.andExpect(jsonPath("$.secondsUntilPreviousReleased").value(600));
			mvc.perform(get("/ana-maria/facultad"))
					.andExpect(status().isFound())
					.andExpect(header().string("Location", "https://undef.edu.ar"));
			mvc.perform(get("/ana/facultad"))
					.andExpect(status().isOk())
					.andExpect(content().string(org.hamcrest.Matchers.containsString("https://sho.rt/ana-maria/facultad")))
					.andExpect(content().string(org.hamcrest.Matchers.containsString("https://undef.edu.ar")));
			mvc.perform(get("/ana"))
					.andExpect(status().isFound())
					.andExpect(header().string("Location", "/ana-maria"));
		}

		@Test
		void afterTheGracePeriodTheOldNameNoLongerWorks() throws Exception {
			rename("ana-maria");
			clock.advance(Duration.ofMinutes(10));

			mvc.perform(get("/ana/facultad")).andExpect(status().isNotFound());
			mvc.perform(get("/ana")).andExpect(status().isNotFound());
			mvc.perform(get("/ana-maria/facultad")).andExpect(status().isFound());
		}

		@Test
		void aThirdNameIsRejectedUntilThePreviousOneIsReleased() throws Exception {
			rename("ana-maria");

			mvc.perform(put("/api/v1/me/username").with(google("google-ana")).with(csrf())
							.contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"otra\"}"))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.previousUsername").value("ana"));
			rename("ana");
			mvc.perform(get("/ana/facultad")).andExpect(status().isFound());
		}

		@Test
		void anotherAccountCannotTakeTheReservedName() throws Exception {
			rename("ana-maria");

			mvc.perform(put("/api/v1/me/username").with(google("google-beto")).with(csrf())
							.contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"ana\"}"))
					.andExpect(status().isConflict());
		}
	}

	@Nested
	@DisplayName("Sesión de la computadora: vence sola y se puede extender")
	class DesktopSessionLimit {

		private MockHttpSession signInByQr() throws Exception {
			db.storeUser("google-ana", "ana", TestBeans.START);
			MockHttpSession computer = new MockHttpSession();
			mvc.perform(post("/api/v1/login-requests").session(computer).with(csrf())).andExpect(status().isCreated());
			String code = (String) computer.getAttribute(LoginRequestController.SESSION_CODE);
			mvc.perform(post("/api/v1/login-requests/" + code + "/approve").with(google("google-ana")).with(csrf()))
					.andExpect(status().isNoContent());
			mvc.perform(get("/api/v1/login-requests/current").session(computer))
					.andExpect(jsonPath("$.status").value("APPROVED"));
			return computer;
		}

		@Test
		void aNewQrDiscardsThePreviousCode() throws Exception {
			db.storeUser("google-ana", "ana", TestBeans.START);
			MockHttpSession computer = new MockHttpSession();
			mvc.perform(post("/api/v1/login-requests").session(computer).with(csrf())).andExpect(status().isCreated());
			String old = (String) computer.getAttribute(LoginRequestController.SESSION_CODE);
			mvc.perform(post("/api/v1/login-requests").session(computer).with(csrf())).andExpect(status().isCreated());

			mvc.perform(post("/api/v1/login-requests/" + old + "/approve").with(google("google-ana")).with(csrf()))
					.andExpect(status().isNotFound());
		}

		@Test
		void sessionReportsItsLimitAndCanBeExtended() throws Exception {
			MockHttpSession computer = signInByQr();
			mvc.perform(get("/api/v1/me/session").session(computer))
					.andExpect(jsonPath("$.limited").value(true))
					.andExpect(jsonPath("$.secondsRemaining").value(1800))
					.andExpect(jsonPath("$.warningSeconds").value(120));

			clock.advance(Duration.ofMinutes(29));
			mvc.perform(post("/api/v1/me/session/extend").session(computer)).andExpect(status().isForbidden());
			mvc.perform(post("/api/v1/me/session/extend").session(computer).with(csrf()))
					.andExpect(jsonPath("$.secondsRemaining").value(1800));

			clock.advance(Duration.ofMinutes(29));
			mvc.perform(get("/api/v1/me").session(computer)).andExpect(status().isOk());
		}

		@Test
		void expiredSessionIsClosed() throws Exception {
			MockHttpSession computer = signInByQr();
			clock.advance(Duration.ofMinutes(30));

			mvc.perform(get("/api/v1/me").session(computer)).andExpect(status().isUnauthorized());
			assertThat(computer.isInvalid()).isTrue();
		}

		@Test
		void phoneSessionsAreNotLimited() throws Exception {
			mvc.perform(get("/api/v1/me/session").with(google("google-ana")))
					.andExpect(jsonPath("$.limited").value(false));
		}
	}

	@Nested
	@DisplayName("Extensión del navegador con la sesión iniciada")
	class Extension {

		private static final String EXTENSION = "chrome-extension://abcdefghijklmnop";

		@Test
		void preflightAllowsCredentialsOnlyForTheAccountApi() throws Exception {
			mvc.perform(options("/api/v1/me/links").header("Origin", EXTENSION)
							.header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "Content-Type"))
					.andExpect(status().isOk())
					.andExpect(header().string("Access-Control-Allow-Origin", EXTENSION))
					.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
			mvc.perform(options("/api/v1/me/links").header("Origin", "https://otro-sitio.com")
							.header("Access-Control-Request-Method", "POST"))
					.andExpect(status().isForbidden());
		}

		@Test
		void createsPersonalLinksFromTheExtensionWithoutTheCsrfToken() throws Exception {
			db.storeUser("google-ana", "ana", TestBeans.START);
			String body = "{\"url\":\"https://undef.edu.ar\",\"alias\":\"\",\"reuseExisting\":true}";

			mvc.perform(post("/api/v1/me/links").header("Origin", EXTENSION).with(google("google-ana"))
							.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isCreated())
					.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
			mvc.perform(post("/api/v1/me/links").header("Origin", "https://otro-sitio.com").with(google("google-ana"))
							.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isForbidden());
			mvc.perform(put("/api/v1/me/username").header("Origin", EXTENSION).with(google("google-ana"))
							.contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"otra\"}"))
					.andExpect(status().isForbidden());
		}
	}
}
