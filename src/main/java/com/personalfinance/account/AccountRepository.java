package com.personalfinance.account;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

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

	/** Locks an authorized account while ledger references or historical properties are changed. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Account a where a.id = :id and a.user.id = :userId")
	Optional<Account> findOwnedForUpdate(Long id, Long userId);

	/**
	 * Lists only one user's accounts in deterministic name/ID order.
	 *
	 * @param userId authenticated user's identifier
	 * @return accounts owned by that user
	 */
	List<Account> findAllByUserIdOrderByNameAscIdAsc(Long userId);
}
