package com.personalfinance.auth;

/** Indicates a registration email conflict without embedding personal data in the exception. */
public class DuplicateEmailException extends RuntimeException {

	/** Creates a safe domain error; the controller chooses the user-facing message. */
	public DuplicateEmailException() {
		super("Registration email is unavailable.");
	}
}
