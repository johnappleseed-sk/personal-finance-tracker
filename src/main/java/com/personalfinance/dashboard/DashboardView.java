package com.personalfinance.dashboard;

import java.math.BigDecimal;
import java.util.List;

import com.personalfinance.account.AccountCurrency;
import com.personalfinance.account.AccountView;
import com.personalfinance.budget.BudgetView;
import com.personalfinance.transaction.TransactionView;

/** Detached read-only financial dashboard summary for the authenticated home screen. */
public record DashboardView(
		String currentMonth,
		List<CurrencySummary> currencySummaries,
		List<AccountView> accounts,
		List<TransactionView> recentTransactions,
		List<CategoryBreakdownItem> topExpenses,
		List<BudgetView> currentMonthBudgets,
		int totalAccounts,
		int totalCategories,
		int totalTransactions
) {

	public record CurrencySummary(
			AccountCurrency currency,
			BigDecimal totalBalance,
			BigDecimal monthlyIncome,
			BigDecimal monthlyExpense,
			BigDecimal monthlyNet,
			int incomeBarPercent,
			int expenseBarPercent,
			int accountCount
	) {
	}

	public record CategoryBreakdownItem(
			String categoryName,
			AccountCurrency currency,
			BigDecimal totalAmount,
			int sharePercent
	) {
	}
}
