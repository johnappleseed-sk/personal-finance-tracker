package com.personalfinance.security;

import java.nio.charset.StandardCharsets;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Rejects passwords beyond BCrypt's 72-byte limit instead of matching a truncated prefix.
 * Registration validates the same limit; this guard also protects Spring Security's login path.
 */
public class StrictBCryptPasswordEncoder implements PasswordEncoder {

	private final BCryptPasswordEncoder delegate;

	/** Uses work factor 12, consistent with existing registration hashes. */
	public StrictBCryptPasswordEncoder() {
		delegate = new BCryptPasswordEncoder(12);
	}

	/**
	 * Encodes without silently truncating input.
	 *
	 * @param rawPassword unmodified password
	 * @return salted BCrypt hash
	 * @throws IllegalArgumentException if input is null or exceeds 72 UTF-8 bytes
	 */
	@Override
	public String encode(CharSequence rawPassword) {
		if (!fitsByteLimit(rawPassword)) {
			throw new IllegalArgumentException("Password must fit within 72 UTF-8 bytes.");
		}
		return delegate.encode(rawPassword);
	}

	/**
	 * Verifies only complete passwords, returning a normal mismatch for oversized input.
	 *
	 * @param rawPassword browser-supplied password, never trimmed
	 * @param encodedPassword stored BCrypt hash
	 * @return whether the entire supplied password matches the hash
	 */
	@Override
	public boolean matches(CharSequence rawPassword, String encodedPassword) {
		return fitsByteLimit(rawPassword) && delegate.matches(rawPassword, encodedPassword);
	}

	/**
	 * Preserves BCrypt's standard work-factor upgrade detection.
	 *
	 * @param encodedPassword stored hash
	 * @return whether BCrypt recommends re-encoding at the configured work factor
	 */
	@Override
	public boolean upgradeEncoding(String encodedPassword) {
		return delegate.upgradeEncoding(encodedPassword);
	}

	private boolean fitsByteLimit(CharSequence password) {
		return password != null && password.length() <= 72
				&& password.toString().getBytes(StandardCharsets.UTF_8).length <= 72;
	}
}
