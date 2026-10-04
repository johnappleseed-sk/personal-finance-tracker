package com.personalfinance.auth;

import java.sql.SQLException;

import com.personalfinance.user.User;
import com.personalfinance.user.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationServiceTests {

	private UserRepository userRepository;
	private PasswordEncoder passwordEncoder;
	private RegistrationService service;
	private RegistrationForm form;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		passwordEncoder = mock(PasswordEncoder.class);
		service = new RegistrationService(userRepository, passwordEncoder);
		form = new RegistrationForm();
		form.setName("  Student  ");
		form.setEmail(" STUDENT@EXAMPLE.TEST ");
		form.setPassword("Synthetic-test-passphrase");
		form.setConfirmPassword(form.getPassword());
		when(passwordEncoder.encode(form.getPassword())).thenReturn("encoded-test-hash");
	}

	@Test
	void onlyEncodedPasswordAndCanonicalIdentityReachRepository() {
		service.register(form);
		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(captor.capture());
		assertThat(captor.getValue().getName()).isEqualTo("Student");
		assertThat(captor.getValue().getEmail()).isEqualTo("student@example.test");
		assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded-test-hash");
	}

	@Test
	void existingEmailIsRejectedBeforeEncodingOrSaving() {
		when(userRepository.existsByEmail(form.getEmail())).thenReturn(true);
		assertThatThrownBy(() -> service.register(form)).isInstanceOf(DuplicateEmailException.class);
		verify(passwordEncoder, never()).encode(any());
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void concurrentUniqueEmailConflictIsTranslatedToSafeDomainError() {
		ConstraintViolationException conflict = new ConstraintViolationException(
				"Unique constraint", new SQLException("test conflict", "23505"), "uk_users_email");
		when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("test", conflict));
		assertThatThrownBy(() -> service.register(form))
				.isInstanceOf(DuplicateEmailException.class)
				.hasMessageNotContaining(form.getEmail())
				.hasMessageNotContaining(form.getPassword());
	}

	@Test
	void unrelatedDatabaseErrorsAreNotMisreportedAsDuplicateEmail() {
		DataIntegrityViolationException failure = new DataIntegrityViolationException("test unrelated constraint");
		when(userRepository.saveAndFlush(any())).thenThrow(failure);
		assertThatThrownBy(() -> service.register(form)).isSameAs(failure);
	}
}
