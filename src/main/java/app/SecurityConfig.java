package app;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lo público sigue igual que en la Etapa 1 (acortar, redirigir, QR). Con sesión: la cuenta, los
 * enlaces personales y aprobar un ingreso por QR. El celular inicia sesión con Google (OAuth2/OIDC);
 * la computadora, aprobando su QR desde el celular (LoginRequestController).
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, DesktopSession desktopSession) throws Exception {
		PathPatternRequestMatcher.Builder path = PathPatternRequestMatcher.withDefaults();
		SavedRequestAwareAuthenticationSuccessHandler afterGoogle = new SavedRequestAwareAuthenticationSuccessHandler();
		afterGoogle.setDefaultTargetUrl("/cuenta.html");
		http
				// Usa la configuración CORS de Application; sin esto, el preflight de la extensión a /api/v1/me
				// no llegaría a Spring MVC: la seguridad lo cortaría con 401.
				.cors(Customizer.withDefaults())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/v1/me", "/api/v1/me/**", "/api/v1/login-requests/*/approve", "/l/*").authenticated()
						.anyRequest().permitAll())
				// La API responde 401; la página del QR lleva directo al login de Google, sin importar qué
				// encabezado Accept mande la app de cámara que la abrió.
				.exceptionHandling(errors -> errors
						.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), path.matcher("/api/**"))
						.defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/google"),
								path.matcher("/l/*")))
				// Quien entra con Google desde un navegador de escritorio queda con la sesión de computadora (D59).
				.oauth2Login(login -> login.successHandler((request, response, authentication) -> {
					if (!DesktopSession.isMobile(request)) {
						desktopSession.start(request.getSession());
					}
					afterGoogle.onAuthenticationSuccess(request, response, authentication);
				}))
				.logout(logout -> logout
						.logoutRequestMatcher(path.matcher(HttpMethod.POST, "/api/v1/logout"))
						.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()))
				// Token en la cookie XSRF-TOKEN, que el JS de las páginas devuelve en X-XSRF-TOKEN. Acortar
				// sigue sin sesión ni token: lo usa la extensión desde otro origen (D45). La extensión crea
				// enlaces personales sin token: su Origin no lo puede falsificar una página (D60).
				.csrf(csrf -> csrf
						.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
						.spa()
						.ignoringRequestMatchers(path.matcher(HttpMethod.POST, "/api/v1/links"),
								fromExtension(path.matcher(HttpMethod.POST, "/api/v1/me/links"))))
				.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
				.addFilterBefore(new DesktopSessionFilter(desktopSession), AnonymousAuthenticationFilter.class);
		return http.build();
	}

	private static RequestMatcher fromExtension(RequestMatcher matcher) {
		return request -> {
			String origin = request.getHeader(HttpHeaders.ORIGIN);
			return matcher.matches(request) && origin != null
					&& (origin.startsWith("chrome-extension://") || origin.startsWith("moz-extension://"));
		};
	}

	/** Cierra la sesión de computadora vencida antes de autorizar: el pedido sigue como anónimo. */
	private static final class DesktopSessionFilter extends OncePerRequestFilter {

		private final DesktopSession desktopSession;

		DesktopSessionFilter(DesktopSession desktopSession) {
			this.desktopSession = desktopSession;
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
				throws ServletException, IOException {
			HttpSession session = request.getSession(false);
			if (session != null && desktopSession.isExpired(session)) {
				session.invalidate();
				SecurityContextHolder.clearContext();
			}
			chain.doFilter(request, response);
		}
	}

	/** El token es diferido: sin leerlo, la cookie no se escribe y las páginas estáticas no lo recibirían. */
	private static final class CsrfCookieFilter extends OncePerRequestFilter {

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
				throws ServletException, IOException {
			CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
			if (token != null) {
				token.getToken();
			}
			chain.doFilter(request, response);
		}
	}
}
