package com.personalfinance.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.personalfinance.account.AccountCurrency;
import com.personalfinance.account.AccountService;
import com.personalfinance.account.AccountView;
import com.personalfinance.budget.BudgetService;
import com.personalfinance.budget.BudgetView;
import com.personalfinance.category.CategoryService;
import com.personalfinance.category.CategoryType;
import com.personalfinance.category.CategoryView;
import com.personalfinance.dashboard.DashboardView.CategoryBreakdownItem;
import com.personalfinance.dashboard.DashboardView.CurrencySummary;
import com.personalfinance.transaction.TransactionService;
import com.personalfinance.transaction.TransactionView;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Aggregates per-currency balances, monthly cash flow, category breakdowns, and budgets for the dashboard. */
@Service
@Validated
@Transactional(readOnly = true)
public class DashboardService {

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
		Map<CategoryCurrencyKey, BigDecimal> expenseByCategory = new LinkedHashMap<>();

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
				expenseByCategory.merge(new CategoryCurrencyKey(tx.categoryName(), tx.currency()),
						tx.amount(), BigDecimal::add);
			}
		}

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
				BigDecimal maxFlow = income.max(expense);
				int incomeBar = maxFlow.compareTo(BigDecimal.ZERO) > 0
						? income.multiply(BigDecimal.valueOf(100)).divide(maxFlow, 0, RoundingMode.HALF_UP).intValue()
						: 0;
				int expenseBar = maxFlow.compareTo(BigDecimal.ZERO) > 0
						? expense.multiply(BigDecimal.valueOf(100)).divide(maxFlow, 0, RoundingMode.HALF_UP).intValue()
						: 0;
				currencySummaries.add(new CurrencySummary(
						currency, balance, income, expense, net, incomeBar, expenseBar, acctCount));
			}
		}

		List<CategoryBreakdownItem> topExpenses = expenseByCategory.entrySet().stream()
				.map(entry -> {
					BigDecimal currencyTotal = totalExpenseByCurrency.getOrDefault(
							entry.getKey().currency(), BigDecimal.ZERO);
					int share = currencyTotal.compareTo(BigDecimal.ZERO) > 0
							? entry.getValue().multiply(BigDecimal.valueOf(100))
									.divide(currencyTotal, 0, RoundingMode.HALF_UP).intValue()
							: 0;
					return new CategoryBreakdownItem(
							entry.getKey().categoryName(),
							entry.getKey().currency(),
							entry.getValue().setScale(2, RoundingMode.UNNECESSARY),
							share);
				})
				.sorted(Comparator.comparing(CategoryBreakdownItem::totalAmount).reversed())
				.limit(6)
				.toList();

		List<TransactionView> recentTransactions = transactions.stream().limit(5).toList();

		return new DashboardView(
				currentMonthStr,
				currencySummaries,
				accounts,
				recentTransactions,
				topExpenses,
				currentMonthBudgets,
				accounts.size(),
				categories.size(),
				transactions.size()
		);
	}
}
