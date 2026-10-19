package com.personalfinance.transfer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Owner-scoped transfer repository and per-account transfer balance delta projections. */
public interface TransferRepository extends JpaRepository<AccountTransfer, Long> {

	@EntityGraph(attributePaths = {"sourceAccount", "targetAccount"})
	List<AccountTransfer> findAllByUserIdOrderByTransferDateDescIdDesc(Long userId);

	@EntityGraph(attributePaths = {"sourceAccount", "targetAccount"})
	Optional<AccountTransfer> findByIdAndUserId(Long id, Long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from AccountTransfer t where t.id = :id and t.userId = :userId")
	Optional<AccountTransfer> findOwnedForUpdate(Long id, Long userId);

	@Query("""
			select count(t) > 0 from AccountTransfer t
			where t.userId = :userId and (t.sourceAccount.id = :accountId or t.targetAccount.id = :accountId)
			""")
	boolean existsByAccountIdAndUserId(Long accountId, Long userId);

	@Query("""
			select t.sourceAccount.id as accountId, sum(t.amount) as totalAmount
			from AccountTransfer t where t.userId = :userId group by t.sourceAccount.id
			""")
	List<AccountTransferTotal> findOutgoingTotals(Long userId);

	@Query("""
			select t.targetAccount.id as accountId, sum(t.amount) as totalAmount
			from AccountTransfer t where t.userId = :userId group by t.targetAccount.id
			""")
	List<AccountTransferTotal> findIncomingTotals(Long userId);

	interface AccountTransferTotal {
		Long getAccountId();
		BigDecimal getTotalAmount();
	}
}
