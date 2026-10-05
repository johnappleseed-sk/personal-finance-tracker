package com.personalfinance.transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Owner-scoped ledger access and exact balance aggregation; no writable cached balances. */
public interface TransactionRepository extends JpaRepository<FinanceTransaction, Long> {

	/** Loads authorized display references in one query, newest date/ID first. */
	@EntityGraph(attributePaths = {"account", "category"})
	List<FinanceTransaction> findAllByUserIdOrderByTransactionDateDescIdDesc(Long userId);

	/** Missing and foreign entries are indistinguishable. */
	@EntityGraph(attributePaths = {"account", "category"})
	Optional<FinanceTransaction> findByIdAndUserId(Long id, Long userId);

	/** Serializes updates/deletes to the same ledger entry; reference locks are acquired separately. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from FinanceTransaction t where t.id = :id and t.userId = :userId")
	Optional<FinanceTransaction> findOwnedForUpdate(Long id, Long userId);

	boolean existsByAccountIdAndUserId(Long accountId, Long userId);
	boolean existsByCategoryIdAndUserId(Long categoryId, Long userId);

	/** One grouped query for all of a user's per-account deltas, never mixing account currencies. */
	@Query("""
			select t.account.id as accountId,
			       sum(case when t.transactionType = com.personalfinance.category.CategoryType.INCOME
			                then t.amount else -t.amount end) as netAmount
			from FinanceTransaction t where t.userId = :userId group by t.account.id
			""")
	List<AccountDelta> findAccountDeltas(Long userId);

	/** Projection of exact income-minus-expense for one account. */
	interface AccountDelta {
		Long getAccountId();
		BigDecimal getNetAmount();
	}
}
