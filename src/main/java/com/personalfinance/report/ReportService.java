package com.personalfinance.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.personalfinance.account.AccountCurrency;
import com.personalfinance.account.AccountService;
import com.personalfinance.account.AccountView;
import com.personalfinance.budget.BudgetService;
import com.personalfinance.budget.BudgetView;
import com.personalfinance.category.CategoryType;
import com.personalfinance.report.ReportView.CategoryAnalyticsRow;
import com.personalfinance.report.ReportView.DssRecommendation;
import com.personalfinance.report.ReportView.DssSummary;
import com.personalfinance.report.ReportView.MonthlyTrendPoint;
import com.personalfinance.transaction.TransactionService;
import com.personalfinance.transaction.TransactionView;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * Management Information Systems (MIS) & Decision Support System (DSS) reporting service.
 * Transforms raw Transaction Processing System (TPS) ledger entries into trend charts,
 * category analytics, Excel-ready CSV summaries, and actionable financial decisions.
 */
@Service
@Validated
@Transactional(readOnly = true)
public class ReportService {

	private static final int TREND_MONTHS = 6;

	private final AccountService accountService;
	private final TransactionService transactionService;
	private final BudgetService budgetService;

	public ReportService(AccountService accountService, TransactionService transactionService,
			BudgetService budgetService) {
		this.accountService = accountService;
		this.transactionService = transactionService;
		this.budgetService = budgetService;
	}

	public ReportView buildReport(@NotNull @Positive Long userId, AccountCurrency requestedCurrency) {
		List<AccountView> accounts = accountService.findAccountsForUser(userId);
		List<TransactionView> allTransactions = transactionService.findTransactionsForUser(userId);

		AccountCurrency currency = resolveCurrency(requestedCurrency, accounts, allTransactions);
		YearMonth currentYm = YearMonth.now();
		YearMonth startYm = currentYm.minusMonths(TREND_MONTHS - 1);

		BigDecimal currentLiquidity = accounts.stream()
				.filter(a -> a.currency() == currency)
				.map(AccountView::currentBalance)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.UNNECESSARY);

		List<TransactionView> currencyTx = allTransactions.stream()
				.filter(tx -> tx.currency() == currency)
				.toList();

		// 1. Build 6-month trend points
		Map<YearMonth, BigDecimal> incomeByMonth = new LinkedHashMap<>();
		Map<YearMonth, BigDecimal> expenseByMonth = new LinkedHashMap<>();
		for (int i = 0; i < TREND_MONTHS; i++) {
			YearMonth ym = startYm.plusMonths(i);
			incomeByMonth.put(ym, BigDecimal.ZERO);
			expenseByMonth.put(ym, BigDecimal.ZERO);
		}

		for (TransactionView tx : currencyTx) {
			YearMonth ym = YearMonth.from(tx.transactionDate());
			if (!ym.isBefore(startYm) && !ym.isAfter(currentYm)) {
				if (tx.transactionType() == CategoryType.INCOME) {
					incomeByMonth.merge(ym, tx.amount(), BigDecimal::add);
				} else {
					expenseByMonth.merge(ym, tx.amount(), BigDecimal::add);
				}
			}
		}

		BigDecimal peakMonthlyAmount = BigDecimal.ZERO;
		for (YearMonth ym : incomeByMonth.keySet()) {
			peakMonthlyAmount = peakMonthlyAmount.max(incomeByMonth.get(ym)).max(expenseByMonth.get(ym));
		}

		List<MonthlyTrendPoint> monthlyTrends = new ArrayList<>();
		BigDecimal sixMonthIncome = BigDecimal.ZERO;
		BigDecimal sixMonthExpense = BigDecimal.ZERO;

		for (YearMonth ym : incomeByMonth.keySet()) {
			BigDecimal inc = incomeByMonth.get(ym).setScale(2, RoundingMode.UNNECESSARY);
			BigDecimal exp = expenseByMonth.get(ym).setScale(2, RoundingMode.UNNECESSARY);
			BigDecimal net = inc.subtract(exp).setScale(2, RoundingMode.UNNECESSARY);
			sixMonthIncome = sixMonthIncome.add(inc);
			sixMonthExpense = sixMonthExpense.add(exp);

			int savingsRate = inc.compareTo(BigDecimal.ZERO) > 0
					? net.multiply(BigDecimal.valueOf(100)).divide(inc, 0, RoundingMode.HALF_UP).intValue()
					: (exp.compareTo(BigDecimal.ZERO) > 0 ? -100 : 0);
			int incBar = peakMonthlyAmount.compareTo(BigDecimal.ZERO) > 0
					? inc.multiply(BigDecimal.valueOf(100)).divide(peakMonthlyAmount, 0, RoundingMode.HALF_UP).intValue()
					: 0;
			int expBar = peakMonthlyAmount.compareTo(BigDecimal.ZERO) > 0
					? exp.multiply(BigDecimal.valueOf(100)).divide(peakMonthlyAmount, 0, RoundingMode.HALF_UP).intValue()
					: 0;

			monthlyTrends.add(new MonthlyTrendPoint(ym.toString(), inc, exp, net, savingsRate, incBar, expBar));
		}

		// 2. Build Category Analytics
		record CatKey(String name, CategoryType type) {}
		Map<CatKey, List<BigDecimal>> amountsByCategory = new LinkedHashMap<>();
		BigDecimal totalIncomeAllTime = BigDecimal.ZERO;
		BigDecimal totalExpenseAllTime = BigDecimal.ZERO;

		for (TransactionView tx : currencyTx) {
			amountsByCategory.computeIfAbsent(new CatKey(tx.categoryName(), tx.transactionType()), k -> new ArrayList<>())
					.add(tx.amount());
			if (tx.transactionType() == CategoryType.INCOME) {
				totalIncomeAllTime = totalIncomeAllTime.add(tx.amount());
			} else {
				totalExpenseAllTime = totalExpenseAllTime.add(tx.amount());
			}
		}

		BigDecimal finalTotalIncome = totalIncomeAllTime;
		BigDecimal finalTotalExpense = totalExpenseAllTime;

		List<CategoryAnalyticsRow> categoryAnalytics = amountsByCategory.entrySet().stream()
				.map(entry -> {
					List<BigDecimal> vals = entry.getValue();
					int count = vals.size();
					BigDecimal sum = vals.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
							.setScale(2, RoundingMode.UNNECESSARY);
					BigDecimal avg = sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
					BigDecimal typeTotal = entry.getKey().type() == CategoryType.INCOME ? finalTotalIncome : finalTotalExpense;
					int share = typeTotal.compareTo(BigDecimal.ZERO) > 0
							? sum.multiply(BigDecimal.valueOf(100)).divide(typeTotal, 0, RoundingMode.HALF_UP).intValue()
							: 0;
					return new CategoryAnalyticsRow(
							entry.getKey().name(),
							entry.getKey().type(),
							count,
							sum,
							avg,
							share
					);
				})
				.sorted(Comparator.comparing(CategoryAnalyticsRow::categoryType)
						.thenComparing(Comparator.comparing(CategoryAnalyticsRow::totalAmount).reversed()))
				.toList();

		// 3. Build DSS Summary & Recommendations
		List<BudgetView> currentBudgets = budgetService.findBudgetsForUser(userId, currentYm.toString()).stream()
				.filter(b -> b.currency() == currency)
				.toList();

		DssSummary dssSummary = buildDssSummary(
				currency,
				currentLiquidity,
				sixMonthIncome.setScale(2, RoundingMode.UNNECESSARY),
				sixMonthExpense.setScale(2, RoundingMode.UNNECESSARY),
				categoryAnalytics,
				currentBudgets
		);

		return new ReportView(
				currency,
				Arrays.asList(AccountCurrency.values()),
				monthlyTrends,
				categoryAnalytics,
				dssSummary
		);
	}

	/** Exports the 6-month MIS trend and category summary in CSV format for Microsoft Excel analysis. */
	public String exportMonthlySummaryCsv(@NotNull @Positive Long userId, AccountCurrency requestedCurrency) {
		ReportView report = buildReport(userId, requestedCurrency);
		StringBuilder csv = new StringBuilder();
		csv.append("Section,Period_Or_Category,Type,Currency,Income_Or_Total,Expense_Or_Average,Net_Or_Count,Rate_Or_Share_Percent\r\n");

		for (MonthlyTrendPoint pt : report.monthlyTrends()) {
			csv.append("Monthly Trend").append(',')
					.append(escapeCsv(pt.month())).append(',')
					.append("Cash Flow").append(',')
					.append(report.selectedCurrency().name()).append(',')
					.append(pt.income().toPlainString()).append(',')
					.append(pt.expense().toPlainString()).append(',')
					.append(pt.netCashFlow().toPlainString()).append(',')
					.append(pt.savingsRatePercent()).append("%\r\n");
		}

		for (CategoryAnalyticsRow row : report.categoryAnalytics()) {
			csv.append("Category Analysis").append(',')
					.append(escapeCsv(row.categoryName())).append(',')
					.append(row.categoryType().getLabel()).append(',')
					.append(report.selectedCurrency().name()).append(',')
					.append(row.totalAmount().toPlainString()).append(',')
					.append(row.averageAmount().toPlainString()).append(',')
					.append(row.transactionCount()).append(',')
					.append(row.sharePercent()).append("%\r\n");
		}
		return csv.toString();
	}

	private AccountCurrency resolveCurrency(AccountCurrency requested, List<AccountView> accounts,
			List<TransactionView> transactions) {
		if (requested != null) {
			return requested;
		}
		if (!accounts.isEmpty()) {
			return accounts.getFirst().currency();
		}
		if (!transactions.isEmpty()) {
			return transactions.getFirst().currency();
		}
		return AccountCurrency.EUR;
	}

	private DssSummary buildDssSummary(AccountCurrency currency, BigDecimal currentLiquidity,
			BigDecimal sixMonthIncome, BigDecimal sixMonthExpense,
			List<CategoryAnalyticsRow> categoryAnalytics, List<BudgetView> currentBudgets) {
		BigDecimal sixMonthNet = sixMonthIncome.subtract(sixMonthExpense).setScale(2, RoundingMode.UNNECESSARY);
		BigDecimal avgMonthlyExpense = sixMonthExpense.divide(BigDecimal.valueOf(TREND_MONTHS), 2, RoundingMode.HALF_UP);
		int savingsRate = sixMonthIncome.compareTo(BigDecimal.ZERO) > 0
				? sixMonthNet.multiply(BigDecimal.valueOf(100)).divide(sixMonthIncome, 0, RoundingMode.HALF_UP).intValue()
				: (sixMonthExpense.compareTo(BigDecimal.ZERO) > 0 ? -100 : 0);

		CategoryAnalyticsRow topExpense = categoryAnalytics.stream()
				.filter(c -> c.categoryType() == CategoryType.EXPENSE)
				.max(Comparator.comparing(CategoryAnalyticsRow::totalAmount))
				.orElse(null);

		String topExpenseName = topExpense != null ? topExpense.categoryName() : "None";
		BigDecimal topExpenseAmt = topExpense != null ? topExpense.totalAmount() : BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

		String healthStatus;
		String healthBadgeClass;
		if (sixMonthIncome.compareTo(BigDecimal.ZERO) == 0 && sixMonthExpense.compareTo(BigDecimal.ZERO) == 0) {
			healthStatus = "No Activity Yet";
			healthBadgeClass = "badge-neutral";
		} else if (savingsRate >= 20) {
			healthStatus = "Strong Surplus";
			healthBadgeClass = "badge-income";
		} else if (savingsRate >= 0) {
			healthStatus = "Positive / Balanced";
			healthBadgeClass = "badge-info";
		} else {
			healthStatus = "Deficit Warning";
			healthBadgeClass = "badge-expense";
		}

		List<DssRecommendation> recommendations = new ArrayList<>();

		// Recommendation 1: Cash-flow & Savings Rate Decision
		if (sixMonthIncome.compareTo(BigDecimal.ZERO) == 0 && sixMonthExpense.compareTo(BigDecimal.ZERO) == 0) {
			recommendations.add(new DssRecommendation(
					"Initialize TPS Data Collection",
					"Onboarding",
					"badge-info",
					"No income or expense transactions are recorded in " + currency + " for the last 6 months.",
					"Record daily income and expenses in the Transaction Processing System (TPS) to enable trend forecasting and budget variance analysis."
			));
		} else if (savingsRate >= 20) {
			BigDecimal investable = sixMonthNet.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
			recommendations.add(new DssRecommendation(
					"Capital Allocation & Investment Opportunity",
					"Surplus (" + savingsRate + "% saved)",
					"badge-income",
					"Your 6-month net cash flow is positive at " + currency + " " + sixMonthNet.toPlainString() + ".",
					"Allocate up to " + currency + " " + investable.toPlainString()
							+ " (50% of net surplus) into a high-yield savings or investment account while keeping liquid reserves."
			));
		} else if (savingsRate >= 0) {
			recommendations.add(new DssRecommendation(
					"Savings Rate Optimization",
					"Moderate (" + savingsRate + "% saved)",
					"badge-warning",
					"Cash flow is positive (" + currency + " " + sixMonthNet.toPlainString()
							+ "), but below the 20% target savings benchmark.",
					"Set monthly category budgets to cap discretionary spending and raise your monthly savings rate toward 20%."
			));
		} else {
			recommendations.add(new DssRecommendation(
					"Cash Flow Deficit Intervention",
					"Deficit Alert",
					"badge-expense",
					"6-month expenses (" + currency + " " + sixMonthExpense.toPlainString()
							+ ") exceed income (" + currency + " " + sixMonthIncome.toPlainString() + ").",
					"Freeze non-essential spending immediately and establish strict monthly budgets on your highest expense categories."
			));
		}

		// Recommendation 2: Top Cost Driver Concentration Decision
		if (topExpense != null) {
			BigDecimal potentialSavings = topExpense.totalAmount().multiply(new BigDecimal("0.15"))
					.setScale(2, RoundingMode.HALF_UP);
			recommendations.add(new DssRecommendation(
					"Primary Cost Driver: " + topExpense.categoryName(),
					topExpense.sharePercent() + "% of Expenses",
					topExpense.sharePercent() >= 50 ? "badge-warning" : "badge-info",
					topExpense.categoryName() + " accounts for " + currency + " " + topExpense.totalAmount().toPlainString()
							+ " across " + topExpense.transactionCount() + " transactions (avg "
							+ currency + " " + topExpense.averageAmount().toPlainString() + ").",
					"Reducing " + topExpense.categoryName() + " spending by 15% will save "
							+ currency + " " + potentialSavings.toPlainString() + " and directly improve net cash flow."
			));
		}

		// Recommendation 3: Budget Variance & Emergency Reserve Decision
		long overBudgetCount = currentBudgets.stream().filter(BudgetView::overBudget).count();
		if (overBudgetCount > 0) {
			recommendations.add(new DssRecommendation(
					"Budget Variance Control",
					overBudgetCount + " Over Budget",
					"badge-expense",
					overBudgetCount + " expense category budget(s) in " + currency + " have exceeded 100% utilization this month.",
					"Review the Budgets dashboard to reallocate limits or defer purchases in over-budget categories until next month."
			));
		} else if (avgMonthlyExpense.compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal targetReserve = avgMonthlyExpense.multiply(BigDecimal.valueOf(3)).setScale(2, RoundingMode.HALF_UP);
			boolean reserveMet = currentLiquidity.compareTo(targetReserve) >= 0;
			recommendations.add(new DssRecommendation(
					"3-Month Emergency Liquidity Benchmark",
					reserveMet ? "Reserve Healthy" : "Build Reserve",
					reserveMet ? "badge-income" : "badge-warning",
					"Based on your average monthly expense (" + currency + " " + avgMonthlyExpense.toPlainString()
							+ "), a 3-month reserve requires " + currency + " " + targetReserve.toPlainString()
							+ " (current balance: " + currency + " " + currentLiquidity.toPlainString() + ").",
					reserveMet
							? "Your liquid account balance covers more than 3 months of expenses. Excess funds can be transferred to long-term savings."
							: "Use Account Transfers to automate monthly transfers into your Savings account until you reach "
									+ currency + " " + targetReserve.toPlainString() + "."
			));
		}

		return new DssSummary(
				currentLiquidity,
				sixMonthIncome,
				sixMonthExpense,
				sixMonthNet,
				avgMonthlyExpense,
				savingsRate,
				topExpenseName,
				topExpenseAmt,
				healthStatus,
				healthBadgeClass,
				recommendations
		);
	}

	private static String escapeCsv(String raw) {
		if (raw == null || raw.isEmpty()) {
			return "";
		}
		String safe = raw;
		char first = safe.charAt(0);
		if (first == '=' || first == '+' || first == '-' || first == '@') {
			safe = "'" + safe;
		}
		if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
			return "\"" + safe.replace("\"", "\"\"") + "\"";
		}
		return safe;
	}
}
