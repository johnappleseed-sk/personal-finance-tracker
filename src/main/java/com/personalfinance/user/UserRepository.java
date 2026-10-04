package com.personalfinance.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persists user identities; financial resource ownership queries belong to their features. */
public interface UserRepository extends JpaRepository<User, Long> {

	/**
	 * Checks whether a canonical email has already been registered.
	 * The database unique constraint remains authoritative for concurrent requests.
	 *
	 * @param email trimmed, lowercase email
	 * @return whether that email is registered
	 */
	boolean existsByEmail(String email);

	/**
	 * Finds a user by canonical email, for internal identity lookups only.
	 *
	 * @param email trimmed, lowercase email
	 * @return matching user, if present
	 */
	Optional<User> findByEmail(String email);
}
