package com.personalfinance.user;

/** Signals that a trusted session identity no longer corresponds to a persisted user. */
public class UserNotFoundException extends RuntimeException {

	/** Creates a safe error without personal information. */
	public UserNotFoundException() {
		super("User identity is no longer available.");
	}
}
