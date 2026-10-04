package com.personalfinance.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Holds only public registration inputs, separate from the persistence entity.
 * Passwords are transient and must never appear in logs or rendered form values.
 */
public class RegistrationForm {

	@NotBlank(message = "Enter your name.")
	@Size(max = 100, message = "Use at most 100 characters for your name.")
	private String name;

	@NotBlank(message = "Enter your email address.")
	@Email(message = "Enter a valid email address.")
	@Size(max = 254, message = "Use at most 254 characters for your email.")
	private String email;

	@NotBlank(message = "Enter a password.")
	@Size(min = 12, max = 72, message = "Use a password between 12 and 72 characters.")
	private String password;

	@NotBlank(message = "Confirm your password.")
	@Size(max = 72, message = "Use at most 72 characters for your password confirmation.")
	private String confirmPassword;

	/**
	 * Enforces BCrypt's byte limit as well as the character limit; Unicode characters
	 * can occupy multiple UTF-8 bytes. Passwords are never trimmed or truncated.
	 *
	 * @return whether the supplied password fits BCrypt's 72-byte input limit
	 */
	@AssertTrue(message = "Your password must fit within 72 UTF-8 bytes; use fewer characters.")
	public boolean isPasswordWithinByteLimit() {
		return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
	}

	/** @return whether confirmation matches; missing input has its own validation message */
	@AssertTrue(message = "The passwords do not match.")
	public boolean isPasswordsMatching() {
		return password == null || confirmPassword == null || Objects.equals(password, confirmPassword);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name == null ? null : name.strip();
	}

	public String getEmail() {
		return email;
	}

	/** Normalizes before validation so browser and service inputs use one canonical identity. */
	public void setEmail(String email) {
		this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getConfirmPassword() {
		return confirmPassword;
	}

	public void setConfirmPassword(String confirmPassword) {
		this.confirmPassword = confirmPassword;
	}
}
