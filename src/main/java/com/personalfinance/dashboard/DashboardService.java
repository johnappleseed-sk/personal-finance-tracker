package com.personalfinance.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.personalfinance.account.AccountCurrency;
import com.personalfinance.account.AccountService;
import com.personalfinance.account.AccountView;
import com.personalfinance.budget.BudgetService;
import com.personalfinance.budget.BudgetView;
import com.personalfinance.category.CategoryService;
import com.personalfinance.category.CategoryType;
import com.personalfinance.category.CategoryView;
import com.personalfinance.dashboard.DashboardView.AccountShareItem;
import com.personalfinance.dashboard.DashboardView.AnalyticsInsight;
import com.personalfinance.dashboard.DashboardView.CategoryBreakdownItem;
import com.personalfinance.dashboard.DashboardView.CurrencySummary;
import com.personalfinance.dashboard.DashboardView.MonthlyChartPoint;
import com.personalfinance.transaction.TransactionService;
import com.personalfinance.transaction.TransactionView;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Aggregates per-currency balances, 6-month trend charts, category distributions, and executive data analysis for the dashboard. */
@Service
@Validated
@Transactional(readOnly = true)
public class DashboardService {

	private static final int TREND_MONTHS = 6;
	private static final String[] PALETTE_CLASSES = {
			"seg-1", "seg-2", "seg-3", "seg-4", "seg-5", "seg-6"
	};

	private final AccountService accountService;
	private final CategoryService categoryService;
	private final TransactionService transactionService;
	private final BudgetService budgetService;

	public DashboardService(AccountService accountService, CategoryService categoryService,
			TransactionService transactionService, BudgetService budgetService) {
		this.accountService = accountService;
		this.categoryService = categoryService;
		this.transactionService = transactionService;
		this.budgetService = budgetService;
	}

	public DashboardView buildDashboard(@NotNull @Positive Long userId) {
		YearMonth currentYm = YearMonth.now();
		YearMonth startYm = currentYm.minusMonths(TREND_MONTHS - 1);
		String currentMonthStr = currentYm.toString();

		List<AccountView> accounts = accountService.findAccountsForUser(userId);
		List<CategoryView> categories = categoryService.findCategoriesForUser(userId);
		List<TransactionView> transactions = transactionService.findTransactionsForUser(userId);
		List<BudgetView> currentMonthBudgets = budgetService.findBudgetsForUser(userId, currentMonthStr);

		Map<AccountCurrency, BigDecimal> balancesByCurrency = new EnumMap<>(AccountCurrency.class);
		Map<AccountCurrency, Integer> accountCountByCurrency = new EnumMap<>(AccountCurrency.class);
		Map<AccountCurrency, BigDecimal> monthlyIncomeByCurrency = new EnumMap<>(AccountCurrency.class);
		Map<AccountCurrency, BigDecimal> monthlyExpenseByCurrency = new EnumMap<>(AccountCurrency.class);
		Map<AccountCurrency, BigDecimal> totalExpenseByCurrency = new EnumMap<>(AccountCurrency.class);

		for (AccountView account : accounts) {
			balancesByCurrency.merge(account.currency(), account.currentBalance(), BigDecimal::add);
			accountCountByCurrency.merge(account.currency(), 1, Integer::sum);
		}

		record CategoryCurrencyKey(String categoryName, AccountCurrency currency) {}
		Map<CategoryCurrencyKey, List<BigDecimal>> expenseAmountsByCategory = new LinkedHashMap<>();

		for (TransactionView tx : transactions) {
			boolean inCurrentMonth = YearMonth.from(tx.transactionDate()).equals(currentYm);
			if (tx.transactionType() == CategoryType.INCOME) {
				if (inCurrentMonth) {
					monthlyIncomeByCurrency.merge(tx.currency(), tx.amount(), BigDecimal::add);
				}
			} else if (tx.transactionType() == CategoryType.EXPENSE) {
				if (inCurrentMonth) {
					monthlyExpenseByCurrency.merge(tx.currency(), tx.amount(), BigDecimal::add);
				}
				totalExpenseByCurrency.merge(tx.currency(), tx.amount(), BigDecimal::add);
				expenseAmountsByCategory
						.computeIfAbsent(new CategoryCurrencyKey(tx.categoryName(), tx.currency()), k -> new ArrayList<>())
						.add(tx.amount());
			}
		}

		List<CategoryBreakdownItem> allExpenseBreakdowns = new ArrayList<>();
		int colorIdx = 0;
		for (Map.Entry<CategoryCurrencyKey, List<BigDecimal>> entry : expenseAmountsByCategory.entrySet()) {
			List<BigDecimal> vals = entry.getValue();
			int count = vals.size();
			BigDecimal sum = vals.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
					.setScale(2, RoundingMode.UNNECESSARY);
			BigDecimal avg = sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
			BigDecimal currencyTotal = totalExpenseByCurrency.getOrDefault(entry.getKey().currency(), BigDecimal.ZERO);
			int share = currencyTotal.compareTo(BigDecimal.ZERO) > 0
					? sum.multiply(BigDecimal.valueOf(100)).divide(currencyTotal, 0, RoundingMode.HALF_UP).intValue()
					: 0;
			String colorClass = PALETTE_CLASSES[colorIdx % PALETTE_CLASSES.length];
			colorIdx++;
			allExpenseBreakdowns.add(new CategoryBreakdownItem(
					entry.getKey().categoryName(),
					entry.getKey().currency(),
					sum,
					avg,
					count,
					share,
					colorClass));
		}

		List<CategoryBreakdownItem> topExpenses = allExpenseBreakdowns.stream()
				.sorted(Comparator.comparing(CategoryBreakdownItem::totalAmount).reversed())
				.limit(6)
				.toList();

		List<CurrencySummary> currencySummaries = new ArrayList<>();
		for (AccountCurrency currency : AccountCurrency.values()) {
			int acctCount = accountCountByCurrency.getOrDefault(currency, 0);
			if (acctCount > 0) {
				BigDecimal balance = balancesByCurrency.getOrDefault(currency, BigDecimal.ZERO)
						.setScale(2, RoundingMode.UNNECESSARY);
				BigDecimal income = monthlyIncomeByCurrency.getOrDefault(currency, BigDecimal.ZERO)
						.setScale(2, RoundingMode.UNNECESSARY);
				BigDecimal expense = monthlyExpenseByCurrency.getOrDefault(currency, BigDecimal.ZERO)
						.setScale(2, RoundingMode.UNNECESSARY);
				BigDecimal net = income.subtract(expense).setScale(2, RoundingMode.UNNECESSARY);
				int monthlySavingsRate = income.compareTo(BigDecimal.ZERO) > 0
						? net.multiply(BigDecimal.valueOf(100)).divide(income, 0, RoundingMode.HALF_UP).intValue()
						: (expense.compareTo(BigDecimal.ZERO) > 0 ? -100 : 0);

				BigDecimal maxFlow = income.max(expense);
				int incomeBar = maxFlow.compareTo(BigDecimal.ZERO) > 0
						? income.multiply(BigDecimal.valueOf(100)).divide(maxFlow, 0, RoundingMode.HALF_UP).intValue()
						: 0;
				int expenseBar = maxFlow.compareTo(BigDecimal.ZERO) > 0
						? expense.multiply(BigDecimal.valueOf(100)).divide(maxFlow, 0, RoundingMode.HALF_UP).intValue()
						: 0;

				// Build 6-month trend points for this currency
				Map<YearMonth, BigDecimal> incByMonth = new LinkedHashMap<>();
				Map<YearMonth, BigDecimal> expByMonth = new LinkedHashMap<>();
				for (int i = 0; i < TREND_MONTHS; i++) {
					YearMonth ym = startYm.plusMonths(i);
					incByMonth.put(ym, BigDecimal.ZERO);
					expByMonth.put(ym, BigDecimal.ZERO);
				}

				for (TransactionView tx : transactions) {
					if (tx.currency() == currency) {
						YearMonth ym = YearMonth.from(tx.transactionDate());
						if (!ym.isBefore(startYm) && !ym.isAfter(currentYm)) {
							if (tx.transactionType() == CategoryType.INCOME) {
								incByMonth.merge(ym, tx.amount(), BigDecimal::add);
							} else {
								expByMonth.merge(ym, tx.amount(), BigDecimal::add);
							}
						}
					}
				}

				BigDecimal peakMonth = BigDecimal.ZERO;
				for (YearMonth ym : incByMonth.keySet()) {
					peakMonth = peakMonth.max(incByMonth.get(ym)).max(expByMonth.get(ym));
				}

				List<MonthlyChartPoint> monthlyTrends = new ArrayList<>();
				BigDecimal sixMoInc = BigDecimal.ZERO;
				BigDecimal sixMoExp = BigDecimal.ZERO;

				for (YearMonth ym : incByMonth.keySet()) {
					BigDecimal mInc = incByMonth.get(ym).setScale(2, RoundingMode.UNNECESSARY);
					BigDecimal mExp = expByMonth.get(ym).setScale(2, RoundingMode.UNNECESSARY);
					BigDecimal mNet = mInc.subtract(mExp).setScale(2, RoundingMode.UNNECESSARY);
					sixMoInc = sixMoInc.add(mInc);
					sixMoExp = sixMoExp.add(mExp);

					int mRate = mInc.compareTo(BigDecimal.ZERO) > 0
							? mNet.multiply(BigDecimal.valueOf(100)).divide(mInc, 0, RoundingMode.HALF_UP).intValue()
							: (mExp.compareTo(BigDecimal.ZERO) > 0 ? -100 : 0);
					int mIncBar = peakMonth.compareTo(BigDecimal.ZERO) > 0
							? mInc.multiply(BigDecimal.valueOf(100)).divide(peakMonth, 0, RoundingMode.HALF_UP).intValue()
							: 0;
					int mExpBar = peakMonth.compareTo(BigDecimal.ZERO) > 0
							? mExp.multiply(BigDecimal.valueOf(100)).divide(peakMonth, 0, RoundingMode.HALF_UP).intValue()
							: 0;
					String shortLabel = ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
					monthlyTrends.add(new MonthlyChartPoint(
							ym.toString(), shortLabel, mInc, mExp, mNet, mRate, mIncBar, mExpBar));
				}

				BigDecimal sixMoNet = sixMoInc.subtract(sixMoExp).setScale(2, RoundingMode.UNNECESSARY);
				int sixMoRate = sixMoInc.compareTo(BigDecimal.ZERO) > 0
						? sixMoNet.multiply(BigDecimal.valueOf(100)).divide(sixMoInc, 0, RoundingMode.HALF_UP).intValue()
						: 0;
				BigDecimal avgMoExp = sixMoExp.divide(BigDecimal.valueOf(TREND_MONTHS), 2, RoundingMode.HALF_UP);
				BigDecimal reserveTarget = avgMoExp.multiply(BigDecimal.valueOf(3)).setScale(2, RoundingMode.HALF_UP);
				String runwayLabel = avgMoExp.compareTo(BigDecimal.ZERO) > 0 && balance.compareTo(BigDecimal.ZERO) > 0
						? balance.divide(avgMoExp, 1, RoundingMode.HALF_UP).toPlainString() + " mo"
						: "N/A";

				// Account distribution within currency
				BigDecimal positiveBalanceTotal = accounts.stream()
						.filter(a -> a.currency() == currency && a.currentBalance().compareTo(BigDecimal.ZERO) > 0)
						.map(AccountView::currentBalance)
						.reduce(BigDecimal.ZERO, BigDecimal::add);

				List<AccountShareItem> accountShares = accounts.stream()
						.filter(a -> a.currency() == currency)
						.map(a -> {
							int share = positiveBalanceTotal.compareTo(BigDecimal.ZERO) > 0
									&& a.currentBalance().compareTo(BigDecimal.ZERO) > 0
									? a.currentBalance().multiply(BigDecimal.valueOf(100))
											.divide(positiveBalanceTotal, 0, RoundingMode.HALF_UP).intValue()
									: 0;
							return new AccountShareItem(
									a.name(),
									a.accountType().getLabel(),
									a.currency(),
									a.currentBalance(),
									share);
						})
						.toList();

				List<CategoryBreakdownItem> currencyCategories = new ArrayList<>();
				List<CategoryBreakdownItem> rawCats = allExpenseBreakdowns.stream()
						.filter(c -> c.currency() == currency)
						.sorted(Comparator.comparing(CategoryBreakdownItem::totalAmount).reversed())
						.toList();
				for (int i = 0; i < rawCats.size(); i++) {
					CategoryBreakdownItem item = rawCats.get(i);
					currencyCategories.add(new CategoryBreakdownItem(
							item.categoryName(),
							item.currency(),
							item.totalAmount(),
							item.averageAmount(),
							item.transactionCount(),
							item.sharePercent(),
							PALETTE_CLASSES[i % PALETTE_CLASSES.length]));
				}

				currencySummaries.add(new CurrencySummary(
						currency,
						balance,
						income,
						expense,
						net,
						monthlySavingsRate,
						incomeBar,
						expenseBar,
						acctCount,
						sixMoInc.setScale(2, RoundingMode.UNNECESSARY),
						sixMoExp.setScale(2, RoundingMode.UNNECESSARY),
						sixMoNet,
						sixMoRate,
						avgMoExp,
						reserveTarget,
						runwayLabel,
						monthlyTrends,
						accountShares,
						currencyCategories));
			}
		}

		int overBudgetCount = (int) currentMonthBudgets.stream().filter(BudgetView::overBudget).count();
		List<AnalyticsInsight> insights = buildExecutiveInsights(currencySummaries, currentMonthBudgets, overBudgetCount, transactions.size());
		List<TransactionView> recentTransactions = transactions.stream().limit(5).toList();

		return new DashboardView(
				currentMonthStr,
				currencySummaries,
				accounts,
				recentTransactions,
				topExpenses,
				currentMonthBudgets,
				insights,
				accounts.size(),
				categories.size(),
				transactions.size(),
				overBudgetCount
		);
	}

	private List<AnalyticsInsight> buildExecutiveInsights(List<CurrencySummary> summaries,
			List<BudgetView> budgets, int overBudgetCount, int totalTxCount) {
		List<AnalyticsInsight> insights = new ArrayList<>();
		if (summaries.isEmpty()) {
			return insights;
		}

		CurrencySummary primary = summaries.getFirst();
		AccountCurrency curr = primary.currency();

		// 1. 6-Month Savings Efficiency Insight
		boolean healthyRate = primary.sixMonthSavingsRatePercent() >= 20;
		insights.add(new AnalyticsInsight(
				"6-Month Savings Efficiency (" + curr + ")",
				healthyRate ? "Above 20% Target" : "Below 20% Target",
				healthyRate ? "badge-income" : "badge-warning",
				primary.sixMonthSavingsRatePercent() + "% Saved",
				"Net 6-month surplus is " + curr + " " + primary.sixMonthNet().toPlainString()
						+ " (Income: " + curr + " " + primary.sixMonthIncome().toPlainString()
						+ " vs. Expense: " + curr + " " + primary.sixMonthExpense().toPlainString() + ")."
		));

		// 2. Liquidity Runway & 3-Month Emergency Reserve Insight
		boolean reserveMet = primary.totalBalance().compareTo(primary.emergencyReserveTarget()) >= 0;
		insights.add(new AnalyticsInsight(
				"Emergency Liquidity Runway (" + curr + ")",
				reserveMet ? "3-Mo Reserve Met" : "Build Reserve",
				reserveMet ? "badge-income" : "badge-warning",
				primary.runwayMonthsLabel() + " Runway",
				"Liquid balance (" + curr + " " + primary.totalBalance().toPlainString()
						+ ") vs. 3-month benchmark (" + curr + " " + primary.emergencyReserveTarget().toPlainString()
						+ " based on " + curr + " " + primary.avgMonthlyExpense().toPlainString() + "/mo avg spend)."
		));

		// 3. Top Cost Driver Concentration Insight
		if (!primary.categoryBreakdown().isEmpty()) {
			CategoryBreakdownItem topCat = primary.categoryBreakdown().getFirst();
			insights.add(new AnalyticsInsight(
					"Primary Cost Driver (" + curr + ")",
					topCat.sharePercent() + "% of Spend",
					topCat.sharePercent() >= 45 ? "badge-warning" : "badge-info",
					topCat.categoryName(),
					"Totaled " + curr + " " + topCat.totalAmount().toPlainString()
							+ " across " + topCat.transactionCount()
							+ " entries (average " + curr + " " + topCat.averageAmount().toPlainString() + " per entry)."
			));
		}

		// 4. Budget Variance & Ledger Volume Insight
		if (overBudgetCount > 0) {
			insights.add(new AnalyticsInsight(
					"Monthly Budget Variance",
					overBudgetCount + " Over Limit",
					"badge-expense",
					overBudgetCount + " Alert(s)",
					overBudgetCount + " expense budget(s) exceeded 100% utilization this month across "
							+ budgets.size() + " active monthly caps."
			));
		} else {
			insights.add(new AnalyticsInsight(
					"TPS Ledger Activity",
					"Active Ledger",
					"badge-info",
					totalTxCount + " Entries",
					"All " + budgets.size() + " configured monthly category budgets are currently within their spending limits."
			));
		}

		return insights;
	}
}
