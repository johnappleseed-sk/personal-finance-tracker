package com.personalfinance.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.personalfinance.account.AccountCurrency;
import com.personalfinance.category.Category;
import com.personalfinance.category.CategoryRepository;
import com.personalfinance.category.CategoryType;
import com.personalfinance.transaction.FinanceTransaction;
import com.personalfinance.transaction.TransactionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Manages monthly expense budgets and computes real-time spending utilization. */
@Service
@Validated
@Transactional(readOnly = true)
public class BudgetService {

	private final BudgetRepository budgetRepository;
	private final CategoryRepository categoryRepository;
	private final TransactionRepository transactionRepository;

	public BudgetService(BudgetRepository budgetRepository, CategoryRepository categoryRepository,
			TransactionRepository transactionRepository) {
		this.budgetRepository = budgetRepository;
		this.categoryRepository = categoryRepository;
		this.transactionRepository = transactionRepository;
	}

	public List<BudgetView> findBudgetsForUser(@NotNull @Positive Long userId, String monthFilter) {
		List<Budget> budgets = (monthFilter != null && !monthFilter.isBlank())
				? budgetRepository.findAllByUserIdAndBudgetMonthOrderByIdDesc(userId, monthFilter.strip())
				: budgetRepository.findAllByUserIdOrderByBudgetMonthDescIdDesc(userId);
		Map<SpendKey, BigDecimal> spendByBucket = computeMonthlyExpenseTotals(userId);
		return budgets.stream()
				.map(budget -> toView(budget, spendByBucket.getOrDefault(
						new SpendKey(budget.getCategory().getId(), budget.getCurrency(), budget.getBudgetMonth()),
						BigDecimal.ZERO)))
				.toList();
	}

	public BudgetView findBudgetForUser(@NotNull @Positive Long userId, @NotNull @Positive Long budgetId) {
		Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
				.orElseThrow(BudgetNotFoundException::new);
		Map<SpendKey, BigDecimal> spendByBucket = computeMonthlyExpenseTotals(userId);
		BigDecimal spent = spendByBucket.getOrDefault(
				new SpendKey(budget.getCategory().getId(), budget.getCurrency(), budget.getBudgetMonth()),
				BigDecimal.ZERO);
		return toView(budget, spent);
	}

	@Transactional
	public BudgetView createBudget(@NotNull @Positive Long userId, @NotNull @Valid BudgetForm form) {
		Category category = findExpenseCategoryForUpdate(userId, form.getCategoryId());
		if (budgetRepository.existsByUserIdAndCategoryIdAndCurrencyAndBudgetMonth(
				userId, category.getId(), form.getCurrency(), form.getBudgetMonth())) {
			throw new BudgetInputException("budgetMonth",
					"A budget already exists for this category, currency, and month.");
		}
		Budget saved = budgetRepository.saveAndFlush(new Budget(userId, category, form));
		return findBudgetForUser(userId, saved.getId());
	}

	@Transactional
	public void updateBudget(@NotNull @Positive Long userId, @NotNull @Positive Long budgetId,
			@NotNull @Valid BudgetForm form) {
		Budget budget = budgetRepository.findOwnedForUpdate(budgetId, userId)
				.orElseThrow(BudgetNotFoundException::new);
		Category category = findExpenseCategoryForUpdate(userId, form.getCategoryId());
		if (budgetRepository.existsByUserIdAndCategoryIdAndCurrencyAndBudgetMonthAndIdNot(
				userId, category.getId(), form.getCurrency(), form.getBudgetMonth(), budgetId)) {
			throw new BudgetInputException("budgetMonth",
					"Another budget already exists for this category, currency, and month.");
		}
		budget.updateDetails(category, form);
		budgetRepository.flush();
	}

	@Transactional
	public void deleteBudget(@NotNull @Positive Long userId, @NotNull @Positive Long budgetId) {
		Budget budget = budgetRepository.findOwnedForUpdate(budgetId, userId)
				.orElseThrow(BudgetNotFoundException::new);
		budgetRepository.delete(budget);
		budgetRepository.flush();
	}

	private Category findExpenseCategoryForUpdate(Long userId, Long categoryId) {
		Category category = categoryRepository.findOwnedForUpdate(categoryId, userId)
				.orElseThrow(() -> new BudgetInputException("categoryId", "Choose an available expense category."));
		if (category.getCategoryType() != CategoryType.EXPENSE) {
			throw new BudgetInputException("categoryId", "Budgets can only be set for Expense categories.");
		}
		return category;
	}

	private Map<SpendKey, BigDecimal> computeMonthlyExpenseTotals(Long userId) {
		Map<SpendKey, BigDecimal> totals = new HashMap<>();
		for (FinanceTransaction tx : transactionRepository.findAllByUserIdOrderByTransactionDateDescIdDesc(userId)) {
			if (tx.getTransactionType() == CategoryType.EXPENSE) {
				String month = YearMonth.from(tx.getTransactionDate()).toString();
				SpendKey key = new SpendKey(tx.getCategory().getId(), tx.getCurrency(), month);
				totals.merge(key, tx.getAmount(), BigDecimal::add);
			}
		}
		return totals;
	}

	private BudgetView toView(Budget budget, BigDecimal spent) {
		BigDecimal limit = budget.getLimitAmount();
		BigDecimal remaining = limit.subtract(spent);
		int usagePercent = limit.compareTo(BigDecimal.ZERO) > 0
				? spent.multiply(BigDecimal.valueOf(100)).divide(limit, 0, RoundingMode.HALF_UP).intValue()
				: 0;
		int progressBarWidth = Math.min(100, Math.max(0, usagePercent));
		boolean overBudget = spent.compareTo(limit) > 0;
		boolean nearLimit = !overBudget && usagePercent >= 80;
		return new BudgetView(
				budget.getId(),
				budget.getCategory().getId(),
				budget.getCategory().getName(),
				budget.getCurrency(),
				budget.getBudgetMonth(),
				limit,
				spent.setScale(2, RoundingMode.UNNECESSARY),
				remaining.setScale(2, RoundingMode.UNNECESSARY),
				usagePercent,
				progressBarWidth,
				overBudget,
				nearLimit
		);
	}

	private record SpendKey(Long categoryId, AccountCurrency currency, String month) {
	}
}
