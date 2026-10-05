package com.personalfinance.security;

import java.util.Locale;

import com.personalfinance.user.User;
import com.personalfinance.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads canonical database identities for Spring Security's standard password authentication provider. */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public DatabaseUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/**
	 * Resolves an email without revealing whether a user exists in error messages.
	 * Password verification and credential erasure are performed by Spring Security.
	 *
	 * @param email browser-supplied email; normalized with the same policy as registration
	 * @return detached security principal with the trusted user's database identifier
	 * @throws UsernameNotFoundException for a missing, oversized, or unknown login name
	 */
	@Override
	@Transactional(readOnly = true)
	public FinanceUserDetails loadUserByUsername(String email) {
		if (email == null || email.isBlank() || email.length() > 254) {
			throw new UsernameNotFoundException("Invalid email or password.");
		}
		String canonicalEmail = email.strip().toLowerCase(Locale.ROOT);
		User user = userRepository.findByEmail(canonicalEmail)
				.orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
		return new FinanceUserDetails(user.getId(), user.getName(), user.getEmail(), user.getPasswordHash());
	}
}
