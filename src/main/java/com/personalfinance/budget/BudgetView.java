package com.personalfinance.budget;

import java.math.BigDecimal;

import com.personalfinance.account.AccountCurrency;

/** Detached view of a monthly budget with calculated actual spending and progress status. */
public record BudgetView(
		Long id,
		Long categoryId,
		String categoryName,
		AccountCurrency currency,
		String budgetMonth,
		BigDecimal limitAmount,
		BigDecimal spentAmount,
		BigDecimal remainingAmount,
		int usagePercent,
		int progressBarWidth,
		boolean overBudget,
		boolean nearLimit
) {
}
