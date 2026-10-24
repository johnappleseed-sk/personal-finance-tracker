package com.personalfinance.dashboard;

import java.math.BigDecimal;
import java.util.List;

import com.personalfinance.account.AccountCurrency;
import com.personalfinance.account.AccountView;
import com.personalfinance.budget.BudgetView;
import com.personalfinance.transaction.TransactionView;

/** Detached read-only financial dashboard summary and data analytics for the authenticated home screen. */
public record DashboardView(
		String currentMonth,
		List<CurrencySummary> currencySummaries,
		List<AccountView> accounts,
		List<TransactionView> recentTransactions,
		List<CategoryBreakdownItem> topExpenses,
		List<BudgetView> currentMonthBudgets,
		List<AnalyticsInsight> insights,
		int totalAccounts,
		int totalCategories,
		int totalTransactions,
		int overBudgetCount
) {

	public record CurrencySummary(
			AccountCurrency currency,
			BigDecimal totalBalance,
			BigDecimal monthlyIncome,
			BigDecimal monthlyExpense,
			BigDecimal monthlyNet,
			int monthlySavingsRatePercent,
			int incomeBarPercent,
			int expenseBarPercent,
			int accountCount,
			BigDecimal sixMonthIncome,
			BigDecimal sixMonthExpense,
			BigDecimal sixMonthNet,
			int sixMonthSavingsRatePercent,
			BigDecimal avgMonthlyExpense,
			BigDecimal emergencyReserveTarget,
			String runwayMonthsLabel,
			List<MonthlyChartPoint> monthlyTrends,
			List<AccountShareItem> accountShares,
			List<CategoryBreakdownItem> categoryBreakdown
	) {
	}

	public record MonthlyChartPoint(
			String month,
			String shortMonth,
			BigDecimal income,
			BigDecimal expense,
			BigDecimal net,
			int savingsRatePercent,
			int incomeBarPercent,
			int expenseBarPercent
	) {
	}

	public record AccountShareItem(
			String accountName,
			String accountTypeLabel,
			AccountCurrency currency,
			BigDecimal balance,
			int sharePercent
	) {
	}

	public record CategoryBreakdownItem(
			String categoryName,
			AccountCurrency currency,
			BigDecimal totalAmount,
			BigDecimal averageAmount,
			int transactionCount,
			int sharePercent,
			String colorClass
	) {
	}

	public record AnalyticsInsight(
			String title,
			String badgeLabel,
			String badgeClass,
			String metricValue,
			String description
	) {
	}
}
