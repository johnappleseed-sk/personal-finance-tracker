package com.personalfinance.security;

import java.util.Optional;

import com.personalfinance.user.User;
import com.personalfinance.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatabaseUserDetailsServiceTests {

	private UserRepository repository;
	private DatabaseUserDetailsService service;

	@BeforeEach
	void setUp() {
		repository = mock(UserRepository.class);
		service = new DatabaseUserDetailsService(repository);
	}

	@Test
	void canonicalEmailResolvesDetachedIdentityWithOnlyUserAuthority() {
		User user = mock(User.class);
		when(user.getId()).thenReturn(42L);
		when(user.getName()).thenReturn("Student");
		when(user.getEmail()).thenReturn("student@example.test");
		when(user.getPasswordHash()).thenReturn("synthetic-hash");
		when(repository.findByEmail("student@example.test")).thenReturn(Optional.of(user));
		FinanceUserDetails principal = service.loadUserByUsername(" STUDENT@EXAMPLE.TEST ");
		assertThat(principal.getUserId()).isEqualTo(42L);
		assertThat(principal.getDisplayName()).isEqualTo("Student");
		assertThat(principal.getUsername()).isEqualTo("student@example.test");
		assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
		principal.eraseCredentials();
		assertThat(principal.getPassword()).isNull();
		assertThat(principal.getUserId()).isEqualTo(42L);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", "   "})
	void missingEmailDoesNotQueryRepository(String email) {
		assertThatThrownBy(() -> service.loadUserByUsername(email))
				.isInstanceOf(UsernameNotFoundException.class).hasMessage("Invalid email or password.");
		verifyNoInteractions(repository);
	}

	@Test
	void oversizedEmailDoesNotQueryRepository() {
		assertThatThrownBy(() -> service.loadUserByUsername("x".repeat(255)))
				.isInstanceOf(UsernameNotFoundException.class).hasMessage("Invalid email or password.");
		verifyNoInteractions(repository);
	}

	@Test
	void unknownEmailDoesNotAppearInException() {
		when(repository.findByEmail("missing@example.test")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.loadUserByUsername("missing@example.test"))
				.isInstanceOf(UsernameNotFoundException.class).hasMessage("Invalid email or password.");
	}
}
