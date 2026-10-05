package com.personalfinance.auth;

import com.personalfinance.security.FinanceUserDetails;
import com.personalfinance.user.User;
import com.personalfinance.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthenticationIntegrationTests {

	private static final String EMAIL = "student@example.test";
	private static final String PASSWORD = "Synthetic-login-passphrase";

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;

	private User registeredUser;

	@BeforeEach
	void createIsolatedTestUser() {
		userRepository.deleteAll();
		registeredUser = userRepository.saveAndFlush(new User("Student", EMAIL, passwordEncoder.encode(PASSWORD)));
	}

	@Test
	void loginPageIsPublicAccessibleAndHasCsrfWithoutGeneratedLoginMarkup() throws Exception {
		mvc.perform(get("/login"))
				.andExpect(status().isOk()).andExpect(view().name("auth/login"))
				.andExpect(content().string(containsString("name=\"_csrf\"")))
				.andExpect(content().string(containsString("autocomplete=\"current-password\"")))
				.andExpect(content().string(containsString("name=\"viewport\"")))
				.andExpect(content().string(containsString("for=\"email\"")))
				.andExpect(unauthenticated());
		mvc.perform(get("/")).andExpect(redirectedUrl("/login"));
		mvc.perform(get("/home")).andExpect(redirectedUrl("/login"));
	}

	@Test
	void successfulLoginNormalizesEmailRotatesSessionAndErasesCredentials() throws Exception {
		MockHttpSession session = new MockHttpSession();
		String originalSessionId = session.getId();
		mvc.perform(post("/login").session(session).with(csrf())
				.param("email", " STUDENT@EXAMPLE.TEST ").param("password", PASSWORD)
				.param("userId", "999999").param("role", "ADMIN"))
				.andExpect(redirectedUrl("/home")).andExpect(authenticated().withUsername(EMAIL));
		assertThat(session.getId()).isNotEqualTo(originalSessionId);
		SecurityContext context = (SecurityContext) session.getAttribute(
				HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		FinanceUserDetails principal = (FinanceUserDetails) context.getAuthentication().getPrincipal();
		assertThat(principal.getUserId()).isEqualTo(registeredUser.getId());
		assertThat(principal.getPassword()).isNull();
		assertThat(context.getAuthentication().getCredentials()).isNull();
		assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
		mvc.perform(get("/home").session(session).param("userId", "999999"))
				.andExpect(status().isOk()).andExpect(model().attribute("displayName", "Student"))
				.andExpect(content().string(not(containsString(registeredUser.getPasswordHash()))))
				.andExpect(content().string(not(containsString(PASSWORD))))
				.andExpect(content().string(not(containsString(EMAIL))))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("Cache-Control", containsString("no-store")));
		mvc.perform(get("/login").session(session)).andExpect(redirectedUrl("/home"));
		mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/home"));
	}

	@ParameterizedTest
	@CsvSource({
			"student@example.test,wrong-synthetic-password",
			"missing@example.test,Synthetic-login-passphrase",
			"invalid-email,Synthetic-login-passphrase",
			"'',Synthetic-login-passphrase",
			"student@example.test,''"
	})
	void badCredentialsUseOneGenericFailureAndNeverAuthenticate(String email, String password) throws Exception {
		mvc.perform(post("/login").with(csrf()).param("email", email).param("password", password))
				.andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
		mvc.perform(get("/login?error")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Invalid email or password. Please try again.")))
				.andExpect(content().string(not(containsString(EMAIL))))
				.andExpect(content().string(not(containsString(PASSWORD))))
				.andExpect(content().string(not(containsString("UsernameNotFoundException"))));
	}

	@Test
	void loginRequiresValidCsrfToken() throws Exception {
		mvc.perform(post("/login").param("email", EMAIL).param("password", PASSWORD))
				.andExpect(status().isForbidden()).andExpect(unauthenticated());
		mvc.perform(post("/login").with(csrf().useInvalidToken()).param("email", EMAIL).param("password", PASSWORD))
				.andExpect(status().isForbidden()).andExpect(unauthenticated());
	}

	@Test
	void bcryptDoesNotAcceptOverlongPasswordThroughPrefixTruncation() throws Exception {
		String boundaryPassword = "é".repeat(36);
		userRepository.deleteAll();
		userRepository.saveAndFlush(new User("Byte Limit", EMAIL, passwordEncoder.encode(boundaryPassword)));
		mvc.perform(post("/login").with(csrf()).param("email", EMAIL).param("password", boundaryPassword + "x"))
				.andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
		mvc.perform(post("/login").with(csrf()).param("email", EMAIL).param("password", boundaryPassword))
				.andExpect(redirectedUrl("/home")).andExpect(authenticated());
	}

	@Test
	void passwordsAreNotTrimmedDuringLogin() throws Exception {
		mvc.perform(post("/login").with(csrf()).param("email", EMAIL).param("password", " " + PASSWORD + " "))
				.andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
	}

	@Test
	void logoutRequiresPostAndCsrfAndInvalidatesAuthenticatedSession() throws Exception {
		MockHttpSession session = login(EMAIL, PASSWORD);
		mvc.perform(get("/logout").session(session));
		assertThat(session.isInvalid()).isFalse();
		mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
		mvc.perform(post("/logout").session(session).with(csrf().useInvalidToken()))
				.andExpect(status().isForbidden());
		mvc.perform(get("/home").session(session)).andExpect(status().isOk()).andExpect(authenticated());
		mvc.perform(post("/logout").session(session).with(csrf()))
				.andExpect(redirectedUrl("/login?logout"))
				.andExpect(cookie().maxAge("JSESSIONID", 0)).andExpect(unauthenticated());
		assertThat(session.isInvalid()).isTrue();
		mvc.perform(get("/home")).andExpect(redirectedUrl("/login"));
		mvc.perform(get("/login?logout")).andExpect(content().string(containsString("You have been signed out.")));
	}

	@Test
	void attackerReturnUrlsAndSavedRequestsCannotControlRedirects() throws Exception {
		MockHttpSession session = new MockHttpSession();
		mvc.perform(get("/accounts").session(session).header("Host", "untrusted.example"))
				.andExpect(redirectedUrl("/login"));
		assertThat(session.getAttribute("SPRING_SECURITY_SAVED_REQUEST")).isNull();
		mvc.perform(post("/login").session(session).with(csrf()).param("email", EMAIL).param("password", PASSWORD)
				.param("redirect", "https://untrusted.example").param("continue", "//untrusted.example"))
				.andExpect(redirectedUrl("/home"));
	}

	@Test
	void twoSessionsRenderOnlyTheirOwnTrustedDisplayNameAndEscapeHtml() throws Exception {
		User second = userRepository.saveAndFlush(new User("<script>second</script>", "second@example.test",
				passwordEncoder.encode(PASSWORD)));
		MockHttpSession firstSession = login(EMAIL, PASSWORD);
		MockHttpSession secondSession = login(second.getEmail(), PASSWORD);
		mvc.perform(get("/home").session(firstSession).param("userId", second.getId().toString()))
				.andExpect(model().attribute("displayName", "Student"))
				.andExpect(content().string(not(containsString("second"))));
		mvc.perform(get("/home").session(secondSession).param("userId", registeredUser.getId().toString()))
				.andExpect(content().string(containsString("&lt;script&gt;second&lt;/script&gt;")))
				.andExpect(content().string(not(containsString("<script>"))))
				.andExpect(content().string(not(containsString("Welcome, <span>Student"))));
	}

	private MockHttpSession login(String email, String password) throws Exception {
		return (MockHttpSession) mvc.perform(post("/login").with(csrf()).param("email", email).param("password", password))
				.andExpect(redirectedUrl("/home")).andExpect(authenticated())
				.andReturn().getRequest().getSession(false);
	}
}
