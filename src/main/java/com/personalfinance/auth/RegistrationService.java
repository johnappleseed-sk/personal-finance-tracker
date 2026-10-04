package com.personalfinance.auth;

import com.personalfinance.user.User;
import com.personalfinance.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Validates registration inputs and persists a canonical identity with a BCrypt password hash. */
@Service
@Validated
public class RegistrationService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * Registers a user without starting an authenticated session.
	 * Method validation also protects calls that do not originate in MVC.
	 *
	 * @param form validated registration details; raw passwords are never persisted
	 * @throws DuplicateEmailException if the email is already registered, including a concurrent insert
	 */
	@Transactional
	public void register(@NotNull @Valid RegistrationForm form) {
		if (userRepository.existsByEmail(form.getEmail())) {
			throw new DuplicateEmailException();
		}
		User user = new User(form.getName(), form.getEmail(), passwordEncoder.encode(form.getPassword()));
		try {
			// Flush inside this boundary so unique conflicts are translated before transaction completion.
			userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException exception) {
			for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
				if (cause instanceof ConstraintViolationException violation
						&& "uk_users_email".equals(violation.getConstraintName())) {
					throw new DuplicateEmailException();
				}
			}
			throw exception;
		}
	}
}
