package com.personalfinance.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import com.personalfinance.account.AccountCurrency;
import com.personalfinance.account.AccountForm;
import com.personalfinance.account.AccountRepository;
import com.personalfinance.account.AccountService;
import com.personalfinance.account.AccountType;
import com.personalfinance.account.AccountView;
import com.personalfinance.budget.BudgetForm;
import com.personalfinance.budget.BudgetRepository;
import com.personalfinance.budget.BudgetService;
import com.personalfinance.budget.BudgetView;
import com.personalfinance.category.CategoryForm;
import com.personalfinance.category.CategoryRepository;
import com.personalfinance.category.CategoryService;
import com.personalfinance.category.CategoryType;
import com.personalfinance.category.CategoryView;
import com.personalfinance.security.FinanceUserDetails;
import com.personalfinance.transaction.HistoryConflictException;
import com.personalfinance.transaction.TransactionForm;
import com.personalfinance.transaction.TransactionRepository;
import com.personalfinance.transaction.TransactionService;
import com.personalfinance.transfer.TransferForm;
import com.personalfinance.transfer.TransferInputException;
import com.personalfinance.transfer.TransferRepository;
import com.personalfinance.transfer.TransferService;
import com.personalfinance.transfer.TransferView;
import com.personalfinance.user.User;
import com.personalfinance.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class NewFeaturesIntegrationTests {

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired private MockMvc mvc;
	@Autowired private UserRepository users;
	@Autowired private AccountService accounts;
	@Autowired private AccountRepository accountRepository;
	@Autowired private CategoryService categories;
	@Autowired private CategoryRepository categoryRepository;
	@Autowired private TransactionService transactions;
	@Autowired private TransactionRepository transactionRepository;
	@Autowired private TransferService transfers;
	@Autowired private TransferRepository transferRepository;
	@Autowired private BudgetService budgets;
	@Autowired private BudgetRepository budgetRepository;

	private User alice;
	private FinanceUserDetails alicePrincipal;
	private AccountView checkingEur;
	private AccountView savingsEur;
	private AccountView walletUsd;
	private CategoryView salaryCategory;
	private CategoryView groceriesCategory;

	@BeforeEach
	void setUp() {
		transferRepository.deleteAll();
		budgetRepository.deleteAll();
		transactionRepository.deleteAll();
		accountRepository.deleteAll();
		categoryRepository.deleteAll();
		users.deleteAll();

		alice = users.saveAndFlush(new User("Alice", "alice@example.test", "synthetic-unused-hash"));
		alicePrincipal = new FinanceUserDetails(alice.getId(), alice.getName(), alice.getEmail(), alice.getPasswordHash());

		checkingEur = accounts.createAccount(alice.getId(), accountForm("Main Checking", "1000.00", AccountCurrency.EUR));
		savingsEur = accounts.createAccount(alice.getId(), accountForm("Emergency Savings", "500.00", AccountCurrency.EUR));
		walletUsd = accounts.createAccount(alice.getId(), accountForm("USD Wallet", "200.00", AccountCurrency.USD));

		salaryCategory = categories.createCategory(alice.getId(), categoryForm("Salary", CategoryType.INCOME));
		groceriesCategory = categories.createCategory(alice.getId(), categoryForm("Groceries", CategoryType.EXPENSE));
	}

	@Test
	void transfersMoveFundsBetweenSameCurrencyAccountsAndProtectHistory() throws Exception {
		mvc.perform(post("/transfers").with(user(alicePrincipal)).with(csrf())
				.param("sourceAccountId", checkingEur.id().toString())
				.param("targetAccountId", savingsEur.id().toString())
				.param("amount", "250.50")
				.param("transferDate", LocalDate.now().toString())
				.param("description", "Monthly savings transfer"))
				.andExpect(redirectedUrl("/transfers"));

		assertThat(accounts.findAccountForUser(alice.getId(), checkingEur.id()).currentBalance())
				.isEqualByComparingTo("749.50");
		assertThat(accounts.findAccountForUser(alice.getId(), savingsEur.id()).currentBalance())
				.isEqualByComparingTo("750.50");

		assertThatThrownBy(() -> accounts.deleteAccount(alice.getId(), checkingEur.id()))
				.isInstanceOf(HistoryConflictException.class);

		TransferForm crossCurrency = new TransferForm();
		crossCurrency.setSourceAccountId(checkingEur.id());
		crossCurrency.setTargetAccountId(walletUsd.id());
		crossCurrency.setAmount(new BigDecimal("10.00"));
		crossCurrency.setTransferDate(LocalDate.now());
		assertThatThrownBy(() -> transfers.createTransfer(alice.getId(), crossCurrency))
				.isInstanceOf(TransferInputException.class);

		TransferView created = transfers.findTransfersForUser(alice.getId()).getFirst();
		mvc.perform(post("/transfers/" + created.id() + "/delete").with(user(alicePrincipal)).with(csrf()))
				.andExpect(redirectedUrl("/transfers"));

		assertThat(accounts.findAccountForUser(alice.getId(), checkingEur.id()).currentBalance())
				.isEqualByComparingTo("1000.00");
		assertThat(accounts.findAccountForUser(alice.getId(), savingsEur.id()).currentBalance())
				.isEqualByComparingTo("500.00");
	}

	@Test
	void budgetsCalculateUtilizationAndFlagOverBudget() throws Exception {
		String currentMonth = YearMonth.now().toString();
		BudgetForm form = new BudgetForm();
		form.setCategoryId(groceriesCategory.id());
		form.setCurrency(AccountCurrency.EUR);
		form.setBudgetMonth(currentMonth);
		form.setLimitAmount(new BigDecimal("100.00"));
		BudgetView budget = budgets.createBudget(alice.getId(), form);
		assertThat(budget.spentAmount()).isEqualByComparingTo("0.00");
		assertThat(budget.overBudget()).isFalse();

		transactions.createTransaction(alice.getId(),
				txForm(checkingEur.id(), groceriesCategory.id(), CategoryType.EXPENSE, "120.00", "Supermarket"));

		BudgetView updated = budgets.findBudgetForUser(alice.getId(), budget.id());
		assertThat(updated.spentAmount()).isEqualByComparingTo("120.00");
		assertThat(updated.remainingAmount()).isEqualByComparingTo("-20.00");
		assertThat(updated.usagePercent()).isEqualTo(120);
		assertThat(updated.overBudget()).isTrue();

		mvc.perform(get("/budgets").with(user(alicePrincipal)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Over-budget alert")))
				.andExpect(content().string(containsString("Groceries")));
	}

	@Test
	void transactionFilteringSearchAndCsvExportWorkCorrectly() throws Exception {
		transactions.createTransaction(alice.getId(),
				txForm(checkingEur.id(), salaryCategory.id(), CategoryType.INCOME, "3000.00", "October payroll"));
		transactions.createTransaction(alice.getId(),
				txForm(checkingEur.id(), groceriesCategory.id(), CategoryType.EXPENSE, "45.20", "=Fresh market groceries"));

		mvc.perform(get("/transactions").with(user(alicePrincipal)).param("q", "payroll"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("October payroll")))
				.andExpect(content().string(not(containsString("Fresh market groceries"))));

		mvc.perform(get("/transactions/export.csv").with(user(alicePrincipal)))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"transactions.csv\""))
				.andExpect(content().string(containsString("Date,Type,Category,Account,Amount,Currency,Description")))
				.andExpect(content().string(containsString("October payroll")))
				.andExpect(content().string(containsString("'=Fresh market groceries")));
	}

	@Test
	void homeDashboardDisplaysSummaryCardsCategoriesAndRecentActivity() throws Exception {
		transactions.createTransaction(alice.getId(),
				txForm(checkingEur.id(), salaryCategory.id(), CategoryType.INCOME, "2000.00", "Salary deposit"));
		transactions.createTransaction(alice.getId(),
				txForm(checkingEur.id(), groceriesCategory.id(), CategoryType.EXPENSE, "80.00", "Weekly food"));

		mvc.perform(get("/home").with(user(alicePrincipal)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Welcome, <span>Alice</span>")))
				.andExpect(content().string(containsString("Net Balance &amp; Monthly Cash Flow")))
				.andExpect(content().string(containsString("3420.00")))
				.andExpect(content().string(containsString("Weekly food")));
	}

	private AccountForm accountForm(String name, String balance, AccountCurrency currency) {
		AccountForm form = new AccountForm();
		form.setName(name);
		form.setAccountType(AccountType.CHECKING);
		form.setInitialBalance(new BigDecimal(balance));
		form.setCurrency(currency);
		return form;
	}

	private CategoryForm categoryForm(String name, CategoryType type) {
		CategoryForm form = new CategoryForm();
		form.setName(name);
		form.setCategoryType(type);
		return form;
	}

	private TransactionForm txForm(Long accountId, Long categoryId, CategoryType type, String amount, String description) {
		TransactionForm form = new TransactionForm();
		form.setAccountId(accountId);
		form.setCategoryId(categoryId);
		form.setTransactionType(type);
		form.setAmount(new BigDecimal(amount));
		form.setTransactionDate(LocalDate.now());
		form.setDescription(description);
		return form;
	}
}
