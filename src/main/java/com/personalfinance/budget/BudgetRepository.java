package com.personalfinance.budget;

import java.util.List;
import java.util.Optional;

import com.personalfinance.account.AccountCurrency;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Owner-scoped monthly budget queries. */
public interface BudgetRepository extends JpaRepository<Budget, Long> {

	@EntityGraph(attributePaths = {"category"})
	List<Budget> findAllByUserIdOrderByBudgetMonthDescIdDesc(Long userId);

	@EntityGraph(attributePaths = {"category"})
	List<Budget> findAllByUserIdAndBudgetMonthOrderByIdDesc(Long userId, String budgetMonth);

	@EntityGraph(attributePaths = {"category"})
	Optional<Budget> findByIdAndUserId(Long id, Long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Budget b where b.id = :id and b.userId = :userId")
	Optional<Budget> findOwnedForUpdate(Long id, Long userId);

	boolean existsByUserIdAndCategoryIdAndCurrencyAndBudgetMonth(
			Long userId, Long categoryId, AccountCurrency currency, String budgetMonth);

	boolean existsByUserIdAndCategoryIdAndCurrencyAndBudgetMonthAndIdNot(
			Long userId, Long categoryId, AccountCurrency currency, String budgetMonth, Long id);
}
