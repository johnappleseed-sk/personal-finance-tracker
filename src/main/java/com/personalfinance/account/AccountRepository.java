package com.personalfinance.account;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Provides owner-scoped account queries; services must never use unscoped ID lookups for account access. */
public interface AccountRepository extends JpaRepository<Account, Long> {

	/**
	 * Finds an account only when it belongs to the trusted user.
	 *
	 * @param id requested account identifier
	 * @param userId authenticated user's identifier
	 * @return account owned by that user, or empty for missing/foreign accounts
	 */
	Optional<Account> findByIdAndUserId(Long id, Long userId);

	/**
	 * Lists only one user's accounts in deterministic name/ID order.
	 *
	 * @param userId authenticated user's identifier
	 * @return accounts owned by that user
	 */
	List<Account> findAllByUserIdOrderByNameAscIdAsc(Long userId);
}
