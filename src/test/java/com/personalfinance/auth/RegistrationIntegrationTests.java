package com.personalfinance.auth;

import com.personalfinance.user.User;
import com.personalfinance.user.UserRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RegistrationIntegrationTests {

	private static final String PASSWORD = "Synthetic-test-passphrase";

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
	private RegistrationService registrationService;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private UserDetailsService userDetailsService;

	@BeforeEach
	void clearIsolatedTestDatabase() {
		userRepository.deleteAll();
	}

	@Test
	void registrationPageIsPublicAndContainsCsrfAndAccessibleFields() throws Exception {
		mvc.perform(get("/register"))
				.andExpect(status().isOk())
				.andExpect(view().name("auth/register"))
				.andExpect(content().string(containsString("name=\"_csrf\"")))
				.andExpect(content().string(containsString("name=\"viewport\"")))
				.andExpect(content().string(containsString("for=\"confirmPassword\"")))
				.andExpect(unauthenticated());
		mvc.perform(get("/css/app.css")).andExpect(status().isOk());
	}

	@Test
	void validRegistrationPersistsCanonicalIdentityAndHashWithoutSigningIn() throws Exception {
		mvc.perform(validRequest("  Student  ", " STUDENT@Example.Test "))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/register/success"))
				.andExpect(unauthenticated());
		User user = userRepository.findByEmail("student@example.test").orElseThrow();
		assertThat(user.getName()).isEqualTo("Student");
		assertThat(user.getId()).isPositive();
		assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2a$12$");
		assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
		assertThat(user.getCreatedAt()).isNotNull();
		assertThat(user.getUpdatedAt()).isEqualTo(user.getCreatedAt());
		mvc.perform(get("/register/success"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString(user.getEmail()))))
				.andExpect(content().string(not(containsString(user.getPasswordHash()))));
	}

	@Test
	void invalidInputsShowErrorsWithoutEchoingPasswordsOrWritingData() throws Exception {
		mvc.perform(post("/register").with(csrf()).param("name", " ").param("email", "not-an-email")
				.param("password", PASSWORD).param("confirmPassword", "Different-test-passphrase"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("registrationForm", "name", "email", "passwordsMatching"))
				.andExpect(content().string(not(containsString(PASSWORD))))
				.andExpect(content().string(not(containsString("Different-test-passphrase"))))
				.andExpect(content().string(containsString("The passwords do not match.")));
		assertThat(userRepository.count()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "short", "            "})
	void weakOrBlankPasswordsAreRejected(String password) throws Exception {
		mvc.perform(post("/register").with(csrf()).param("name", "Student")
				.param("email", "student@example.test").param("password", password).param("confirmPassword", password))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("registrationForm", "password"));
		assertThat(userRepository.count()).isZero();
	}

	@Test
	void bcryptByteLimitRejectsMultibytePasswordsWithoutTruncation() throws Exception {
		String password = "é".repeat(37);
		mvc.perform(post("/register").with(csrf()).param("name", "Student")
				.param("email", "student@example.test").param("password", password).param("confirmPassword", password))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("registrationForm", "passwordWithinByteLimit"));
		assertThat(userRepository.count()).isZero();
	}

	@Test
	void bcryptAcceptsExactlySeventyTwoBytes() {
		RegistrationForm form = validForm();
		form.setPassword("é".repeat(36));
		form.setConfirmPassword(form.getPassword());
		registrationService.register(form);
		User user = userRepository.findByEmail(form.getEmail()).orElseThrow();
		assertThat(passwordEncoder.matches(form.getPassword(), user.getPasswordHash())).isTrue();
	}

	@Test
	void duplicateCanonicalEmailDoesNotCreateAnotherUser() throws Exception {
		registrationService.register(validForm());
		mvc.perform(validRequest("Student", " STUDENT@EXAMPLE.TEST "))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("registrationForm", "email"))
				.andExpect(content().string(not(containsString(PASSWORD))))
				.andExpect(content().string(containsString("Registration could not be completed")));
		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void csrfIsRequiredAndProtectedRoutesRemainProtected() throws Exception {
		mvc.perform(post("/register").param("name", "Student"))
				.andExpect(status().isForbidden());
		mvc.perform(validRequest().with(csrf().useInvalidToken()))
				.andExpect(status().isForbidden());
		mvc.perform(get("/accounts")).andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
		assertThat(userRepository.count()).isZero();
	}

	@Test
	void oversizedInputsAreRejectedBeforePersistence() throws Exception {
		String password = "x".repeat(73);
		mvc.perform(post("/register").with(csrf()).param("name", "n".repeat(101))
				.param("email", "e".repeat(250) + "@example.test")
				.param("password", password).param("confirmPassword", password))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("registrationForm",
						"name", "email", "password", "confirmPassword"))
				.andExpect(content().string(not(containsString(password))));
		assertThat(userRepository.count()).isZero();
	}

	@Test
	void serviceValidationRejectsInvalidInputsOutsideMvc() {
		RegistrationForm form = validForm();
		form.setEmail("not-an-email");
		assertThatThrownBy(() -> registrationService.register(form)).isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> registrationService.register(null)).isInstanceOf(ConstraintViolationException.class);
		assertThat(userRepository.count()).isZero();
	}

	@Test
	void unexpectedIdentityFieldsCannotBeMassAssignedAndHtmlIsEscaped() throws Exception {
		mvc.perform(validRequest("<script>alert('test')</script>", "student@example.test")
				.param("id", "999999").param("passwordHash", "injected-hash").param("role", "ADMIN"))
				.andExpect(redirectedUrl("/register/success"));
		User user = userRepository.findByEmail("student@example.test").orElseThrow();
		assertThat(user.getId()).isNotEqualTo(999999L);
		assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
		mvc.perform(post("/register").with(csrf()).param("name", "<script>alert('test')</script>")
				.param("email", "invalid"))
				.andExpect(content().string(not(containsString("<script>"))))
				.andExpect(content().string(containsString("&lt;script&gt;")));
	}

	@Test
	void noGeneratedDevelopmentAccountCanAuthenticate() throws Exception {
		assertThatThrownBy(() -> userDetailsService.loadUserByUsername("user"))
				.isInstanceOf(UsernameNotFoundException.class);
		mvc.perform(post("/login").with(csrf()).param("email", "user").param("password", PASSWORD))
				.andExpect(redirectedUrl("/login?error"))
				.andExpect(unauthenticated());
	}

	private MockHttpServletRequestBuilder validRequest() {
		return validRequest("Student", "student@example.test");
	}

	private MockHttpServletRequestBuilder validRequest(String name, String email) {
		return post("/register").with(csrf()).param("name", name).param("email", email)
				.param("password", PASSWORD).param("confirmPassword", PASSWORD);
	}

	private RegistrationForm validForm() {
		RegistrationForm form = new RegistrationForm();
		form.setName("Student");
		form.setEmail("student@example.test");
		form.setPassword(PASSWORD);
		form.setConfirmPassword(PASSWORD);
		return form;
	}
}
