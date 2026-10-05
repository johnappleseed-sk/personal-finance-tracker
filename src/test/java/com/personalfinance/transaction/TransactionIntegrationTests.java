package com.personalfinance.transaction;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.personalfinance.account.AccountCurrency;
import com.personalfinance.account.AccountForm;
import com.personalfinance.account.AccountRepository;
import com.personalfinance.account.AccountService;
import com.personalfinance.account.AccountType;
import com.personalfinance.account.AccountView;
import com.personalfinance.category.CategoryForm;
import com.personalfinance.category.CategoryRepository;
import com.personalfinance.category.CategoryService;
import com.personalfinance.category.CategoryType;
import com.personalfinance.category.CategoryView;
import com.personalfinance.security.FinanceUserDetails;
import com.personalfinance.user.User;
import com.personalfinance.user.UserRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransactionIntegrationTests {

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired private MockMvc mvc;
	@Autowired private TransactionService service;
	@Autowired private TransactionRepository repository;
	@Autowired private AccountService accounts;
	@Autowired private AccountRepository accountRepository;
	@Autowired private CategoryService categories;
	@Autowired private CategoryRepository categoryRepository;
	@Autowired private UserRepository users;
	@Autowired private DataSource dataSource;

	private User alice;
	private User bob;
	private FinanceUserDetails alicePrincipal;
	private FinanceUserDetails bobPrincipal;
	private AccountView euro;
	private AccountView dollars;
	private AccountView bobAccount;
	private CategoryView income;
	private CategoryView expense;
	private CategoryView bobCategory;

	@BeforeEach
	void setUpIsolatedLedger() {
		repository.deleteAll();
		accountRepository.deleteAll();
		categoryRepository.deleteAll();
		users.deleteAll();
		alice = users.saveAndFlush(new User("Alice", "alice@example.test", "synthetic-unused-hash"));
		bob = users.saveAndFlush(new User("Bob", "bob@example.test", "synthetic-unused-hash"));
		alicePrincipal = principal(alice);
		bobPrincipal = principal(bob);
		euro = accounts.createAccount(alice.getId(), accountForm("Euro account", "100.00", AccountCurrency.EUR));
		dollars = accounts.createAccount(alice.getId(), accountForm("Dollar account", "-10.00", AccountCurrency.USD));
		bobAccount = accounts.createAccount(bob.getId(), accountForm("Bob private account", "999.00", AccountCurrency.GBP));
		income = categories.createCategory(alice.getId(), categoryForm("Salary", CategoryType.INCOME));
		expense = categories.createCategory(alice.getId(), categoryForm("Groceries", CategoryType.EXPENSE));
		bobCategory = categories.createCategory(bob.getId(), categoryForm("Bob private category", CategoryType.EXPENSE));
	}

	@Test
	void pagesRequireAuthenticationAndFormChoicesArePrivate() throws Exception {
		for (String path : List.of("/transactions", "/transactions/new", "/transactions/1/edit", "/transactions/1/delete")) {
			mvc.perform(get(path)).andExpect(redirectedUrl("/login"));
		}
		mvc.perform(get("/transactions").with(user(alicePrincipal)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("No transactions yet.")));
		mvc.perform(get("/transactions/new").with(user(alicePrincipal)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")))
				.andExpect(content().string(containsString("for=\"amount\"")))
				.andExpect(content().string(not(containsString("Bob private"))));
	}

	@Test
	void createsExactLedgerEntryInferringOwnerAndCurrencyDespiteForgedInput() throws Exception {
		mvc.perform(validRequest("/transactions", euro.id(), expense.id(), "EXPENSE", "0.10")
				.param("userId", bob.getId().toString()).param("user.id", bob.getId().toString())
				.param("id", "999999").param("currency", "GBP").param("createdAt", "2000-01-01T00:00:00Z"))
				.andExpect(redirectedUrl("/transactions")).andExpect(flash().attribute("successMessage", "Transaction created."));
		TransactionView saved = service.findTransactionsForUser(alice.getId()).getFirst();
		assertThat(saved.id()).isNotEqualTo(999999L);
		assertThat(saved.currency()).isEqualTo(AccountCurrency.EUR);
		assertThat(saved.amount()).isEqualByComparingTo("0.10");
		assertThat(saved.amount().scale()).isEqualTo(2);
		assertThat(saved.description()).isEqualTo("Test description");
		assertThat(service.findTransactionsForUser(bob.getId())).isEmpty();
		var entity = repository.findByIdAndUserId(saved.id(), alice.getId()).orElseThrow();
		assertThat(entity.getCreatedAt()).isNotNull();
		assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
		assertBalance(euro.id(), "99.90");
	}

	@Test
	void exactBalancesReflectCreateEditMoveTypeChangeDeleteAndOpeningCorrection() {
		TransactionView first = service.createTransaction(alice.getId(), form(euro.id(), income.id(), CategoryType.INCOME, "0.10"));
		TransactionView second = service.createTransaction(alice.getId(), form(euro.id(), income.id(), CategoryType.INCOME, "0.20"));
		TransactionView spent = service.createTransaction(alice.getId(), form(euro.id(), expense.id(), CategoryType.EXPENSE, "0.30"));
		assertBalance(euro.id(), "100.00");
		service.updateTransaction(alice.getId(), spent.id(), form(euro.id(), expense.id(), CategoryType.EXPENSE, "25.00"));
		assertBalance(euro.id(), "75.30");
		// Moving an entry deliberately records the given numeric amount in the new account currency, without conversion.
		service.updateTransaction(alice.getId(), spent.id(), form(dollars.id(), income.id(), CategoryType.INCOME, "25.00"));
		assertBalance(euro.id(), "100.30");
		assertBalance(dollars.id(), "15.00");
		assertThat(service.findTransactionForUser(alice.getId(), spent.id()).currency()).isEqualTo(AccountCurrency.USD);
		accounts.updateAccount(alice.getId(), euro.id(), accountForm("Corrected", "200.00", AccountCurrency.EUR));
		assertBalance(euro.id(), "200.30");
		service.deleteTransaction(alice.getId(), first.id());
		service.deleteTransaction(alice.getId(), second.id());
		service.deleteTransaction(alice.getId(), spent.id());
		assertBalance(euro.id(), "200.00");
		assertBalance(dollars.id(), "-10.00");
		assertThat(accounts.findAccountsForUser(bob.getId()).getFirst().currentBalance()).isEqualByComparingTo("999.00");
	}

	@Test
	void currentBalanceCanExceedSingleEntryPrecisionWithoutOverflow() {
		service.createTransaction(alice.getId(), form(euro.id(), income.id(), CategoryType.INCOME, "99999999999999999.99"));
		service.createTransaction(alice.getId(), form(euro.id(), income.id(), CategoryType.INCOME, "99999999999999999.99"));
		assertBalance(euro.id(), "200000000000000099.98");
	}

	@Test
	void newestDateAndIdOrderingAndEscapedDescriptionsAreRendered() throws Exception {
		TransactionForm older = form(euro.id(), expense.id(), CategoryType.EXPENSE, "1.00");
		older.setTransactionDate(LocalDate.now().minusDays(1));
		var first = service.createTransaction(alice.getId(), older);
		TransactionForm today = form(euro.id(), income.id(), CategoryType.INCOME, "2.00");
		today.setDescription("<script>alert(1)</script>");
		var second = service.createTransaction(alice.getId(), today);
		var third = service.createTransaction(alice.getId(), today);
		service.createTransaction(bob.getId(), form(bobAccount.id(), bobCategory.id(), CategoryType.EXPENSE, "99.00"));
		assertThat(service.findTransactionsForUser(alice.getId())).extracting(TransactionView::id)
				.containsExactly(third.id(), second.id(), first.id());
		for (String path : List.of("/transactions", "/transactions/" + second.id() + "/edit")) {
			mvc.perform(get(path).with(user(alicePrincipal))).andExpect(status().isOk())
					.andExpect(content().string(containsString("&lt;script&gt;")))
					.andExpect(content().string(not(containsString("<script>"))))
					.andExpect(content().string(not(containsString("Bob private"))));
		}
	}

	@Test
	void missingAndForeignEntriesAreIdentical404OnAllOperationsIncludingInvalidEdit() throws Exception {
		var saved = service.createTransaction(alice.getId(), form(euro.id(), income.id(), CategoryType.INCOME, "2.00"));
		for (Long id : List.of(saved.id(), Long.MAX_VALUE)) {
			for (String suffix : List.of("/edit", "/delete")) {
				mvc.perform(get("/transactions/" + id + suffix).with(user(bobPrincipal)))
						.andExpect(status().isNotFound()).andExpect(view().name("transactions/not-found"))
						.andExpect(content().string(not(containsString("Euro account"))));
			}
			mvc.perform(post("/transactions/" + id).with(user(bobPrincipal)).with(csrf()))
					.andExpect(status().isNotFound());
			mvc.perform(post("/transactions/" + id).with(user(bobPrincipal)).with(csrf())
					.param("accountId", bobAccount.id().toString()).param("categoryId", bobCategory.id().toString())
					.param("transactionType", "EXPENSE").param("amount", "1.00").param("transactionDate", LocalDate.now().toString()))
					.andExpect(status().isNotFound());
			mvc.perform(post("/transactions/" + id + "/delete").with(user(bobPrincipal)).with(csrf()))
					.andExpect(status().isNotFound());
		}
		assertThatThrownBy(() -> service.findTransactionForUser(bob.getId(), saved.id())).isInstanceOf(TransactionNotFoundException.class);
		assertThatThrownBy(() -> service.updateTransaction(bob.getId(), saved.id(), form(bobAccount.id(), bobCategory.id(), CategoryType.EXPENSE, "1.00")))
				.isInstanceOf(TransactionNotFoundException.class);
		assertThatThrownBy(() -> service.deleteTransaction(bob.getId(), saved.id())).isInstanceOf(TransactionNotFoundException.class);
		assertBalance(euro.id(), "102.00");
	}

	@Test
	void forgedOrMissingReferencesAndMismatchedTypeAreSafeFieldErrorsWithoutWrites() throws Exception {
		for (Long accountId : List.of(bobAccount.id(), Long.MAX_VALUE)) {
			mvc.perform(validRequest("/transactions", accountId, expense.id(), "EXPENSE", "1.00"))
					.andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("transactionForm", "accountId"))
					.andExpect(content().string(not(containsString("Bob private"))));
		}
		for (Long categoryId : List.of(bobCategory.id(), Long.MAX_VALUE)) {
			mvc.perform(validRequest("/transactions", euro.id(), categoryId, "EXPENSE", "1.00"))
					.andExpect(model().attributeHasFieldErrors("transactionForm", "categoryId"));
		}
		mvc.perform(validRequest("/transactions", euro.id(), expense.id(), "INCOME", "1.00"))
				.andExpect(model().attributeHasFieldErrors("transactionForm", "categoryId"))
				.andExpect(content().string(containsString("matching the transaction type")));
		assertThatThrownBy(() -> service.createTransaction(alice.getId(), form(bobAccount.id(), expense.id(), CategoryType.EXPENSE, "1.00")))
				.isInstanceOf(TransactionInputException.class);
		assertThat(repository.count()).isZero();
		assertBalance(euro.id(), "100.00");
	}

	@Test
	void invalidEditRollsBackAndPreservesOwnerCreationTimestampAndBalance() throws Exception {
		var saved = service.createTransaction(alice.getId(), form(euro.id(), income.id(), CategoryType.INCOME, "2.00"));
		var before = repository.findByIdAndUserId(saved.id(), alice.getId()).orElseThrow();
		mvc.perform(validRequest("/transactions/" + saved.id(), euro.id(), expense.id(), "INCOME", "100.00"))
				.andExpect(model().attributeHasFieldErrors("transactionForm", "categoryId"));
		assertThat(service.findTransactionForUser(alice.getId(), saved.id())).isEqualTo(saved);
		mvc.perform(validRequest("/transactions/" + saved.id(), euro.id(), expense.id(), "EXPENSE", "3.00")
				.param("userId", bob.getId().toString())).andExpect(redirectedUrl("/transactions"));
		var after = repository.findByIdAndUserId(saved.id(), alice.getId()).orElseThrow();
		assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
		assertThat(after.getUpdatedAt()).isAfterOrEqualTo(before.getUpdatedAt());
		assertThat(repository.findByIdAndUserId(saved.id(), bob.getId())).isEmpty();
		assertBalance(euro.id(), "97.00");
	}

	@Test
	void everyMutationRequiresAuthenticationAndCsrfAndGetDeleteIsReadOnly() throws Exception {
		var saved = service.createTransaction(alice.getId(), form(euro.id(), expense.id(), CategoryType.EXPENSE, "1.00"));
		for (String path : List.of("/transactions", "/transactions/" + saved.id(), "/transactions/" + saved.id() + "/delete")) {
			mvc.perform(post(path).with(csrf())).andExpect(redirectedUrl("/login"));
			mvc.perform(post(path).with(user(alicePrincipal))).andExpect(status().isForbidden());
			mvc.perform(post(path).with(user(alicePrincipal)).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
		}
		mvc.perform(get("/transactions/{id}/delete", saved.id()).with(user(alicePrincipal)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("cannot be undone")));
		assertBalance(euro.id(), "99.00");
		mvc.perform(post("/transactions/{id}/delete", saved.id()).with(user(alicePrincipal)).with(csrf()))
				.andExpect(redirectedUrl("/transactions")).andExpect(flash().attribute("successMessage", "Transaction deleted."));
		assertBalance(euro.id(), "100.00");
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-1.00", "1.001", "100000000000000000.00", "NaN", "Infinity", "not-money", ""})
	void invalidAmountsAreRejectedWithoutRounding(String amount) throws Exception {
		mvc.perform(validRequest("/transactions", euro.id(), expense.id(), "EXPENSE", amount))
				.andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("transactionForm", "amount"));
		assertThat(repository.count()).isZero();
	}

	@Test
	void requiredFieldsDatesEnumsAndDescriptionAreValidatedInMvcAndService() throws Exception {
		mvc.perform(post("/transactions").with(user(alicePrincipal)).with(csrf()))
				.andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("transactionForm",
						"accountId", "categoryId", "transactionType", "amount", "transactionDate"));
		for (String date : List.of("invalid", "2026-02-30", LocalDate.now().plusDays(1).toString())) {
			mvc.perform(validRequest("/transactions", euro.id(), expense.id(), "EXPENSE", "1.00")
					.with(request -> { request.setParameter("transactionDate", date); return request; }))
					.andExpect(model().attributeHasFieldErrors("transactionForm", "transactionDate"));
		}
		mvc.perform(validRequest("/transactions", euro.id(), expense.id(), "TRANSFER", "1.00"))
				.andExpect(model().attributeHasFieldErrors("transactionForm", "transactionType"));
		mvc.perform(validRequest("/transactions", euro.id(), expense.id(), "EXPENSE", "1.00")
				.with(request -> { request.setParameter("description", "x".repeat(256)); return request; }))
				.andExpect(model().attributeHasFieldErrors("transactionForm", "description"));
		assertThatThrownBy(() -> service.createTransaction(alice.getId(), form(euro.id(), expense.id(), CategoryType.EXPENSE, "1.001")))
				.isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> service.createTransaction(alice.getId(), null)).isInstanceOf(ConstraintViolationException.class);
		assertThat(repository.count()).isZero();
	}

	@Test
	void missingSetupIsExplainedAndOptionalDescriptionAcceptsItsBoundary() throws Exception {
		mvc.perform(get("/transactions/new").with(user(new FinanceUserDetails(Long.MAX_VALUE, "Missing", "missing@example.test", "unused"))))
				.andExpect(status().isOk()).andExpect(content().string(containsString("You need an account and a matching category first.")));
		TransactionForm form = form(euro.id(), income.id(), CategoryType.INCOME, "0.01");
		form.setDescription("x".repeat(255));
		assertThat(service.createTransaction(alice.getId(), form).description()).hasSize(255);
		form.setDescription(null);
		assertThat(service.createTransaction(alice.getId(), form).description()).isEmpty();
		form.setTransactionDate(LocalDate.now().plusDays(1));
		assertThatThrownBy(() -> service.createTransaction(alice.getId(), form)).isInstanceOf(ConstraintViolationException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-1", "not-a-number", "9223372036854775808"})
	void malformedTransactionIdsAreSafeBadRequests(String id) throws Exception {
		mvc.perform(get("/transactions/" + id + "/edit").with(user(alicePrincipal))).andExpect(status().isBadRequest());
		mvc.perform(validRequest("/transactions/" + id, euro.id(), expense.id(), "EXPENSE", "1.00"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/transactions/" + id + "/delete").with(user(alicePrincipal)).with(csrf()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void usedAccountsAndCategoriesCannotBeDeletedOrReinterpretedButCanBeRenamed() throws Exception {
		var saved = service.createTransaction(alice.getId(), form(euro.id(), expense.id(), CategoryType.EXPENSE, "1.00"));
		assertThatThrownBy(() -> accounts.deleteAccount(alice.getId(), euro.id())).isInstanceOf(HistoryConflictException.class);
		assertThatThrownBy(() -> categories.deleteCategory(alice.getId(), expense.id())).isInstanceOf(HistoryConflictException.class);
		assertThatThrownBy(() -> accounts.updateAccount(alice.getId(), euro.id(), accountForm("Changed", "100.00", AccountCurrency.GBP)))
				.isInstanceOf(HistoryConflictException.class);
		assertThatThrownBy(() -> categories.updateCategory(alice.getId(), expense.id(), categoryForm("Changed", CategoryType.INCOME)))
				.isInstanceOf(HistoryConflictException.class);
		mvc.perform(post("/accounts/{id}/delete", euro.id()).with(user(alicePrincipal)).with(csrf()))
				.andExpect(status().isConflict()).andExpect(view().name("transactions/history-conflict"));
		mvc.perform(post("/categories/{id}/delete", expense.id()).with(user(alicePrincipal)).with(csrf()))
				.andExpect(status().isConflict()).andExpect(content().string(containsString("cannot be deleted")));
		mvc.perform(post("/accounts/{id}", euro.id()).with(user(alicePrincipal)).with(csrf())
				.param("name", "Changed").param("accountType", "CHECKING").param("initialBalance", "100.00").param("currency", "GBP"))
				.andExpect(status().isConflict()).andExpect(content().string(containsString("cannot change currency")));
		mvc.perform(post("/categories/{id}", expense.id()).with(user(alicePrincipal)).with(csrf())
				.param("name", "Changed").param("categoryType", "INCOME"))
				.andExpect(status().isConflict()).andExpect(content().string(containsString("cannot change type")));
		accounts.updateAccount(alice.getId(), euro.id(), accountForm("Renamed account", "100.00", AccountCurrency.EUR));
		categories.updateCategory(alice.getId(), expense.id(), categoryForm("Renamed category", CategoryType.EXPENSE));
		assertThat(service.findTransactionForUser(alice.getId(), saved.id()).accountName()).isEqualTo("Renamed account");
		assertBalance(euro.id(), "99.00");
		service.deleteTransaction(alice.getId(), saved.id());
		accounts.deleteAccount(alice.getId(), euro.id());
		categories.deleteCategory(alice.getId(), expense.id());
	}

	@Test
	void simultaneousWritesDoNotLoseBalanceUpdates() throws Exception {
		try (var executor = Executors.newFixedThreadPool(4)) {
			List<Callable<TransactionView>> tasks = new ArrayList<>();
			for (int i = 0; i < 12; i++) {
				tasks.add(() -> service.createTransaction(alice.getId(), form(euro.id(), income.id(), CategoryType.INCOME, "0.10")));
			}
			for (var future : executor.invokeAll(tasks, 30, TimeUnit.SECONDS)) {
				assertThat(future.get(1, TimeUnit.SECONDS).amount()).isEqualByComparingTo("0.10");
			}
		}
		assertThat(repository.count()).isEqualTo(12);
		assertBalance(euro.id(), "101.20");
	}

	@Test
	void databaseCompositeKeysRejectForeignOwnersCurrencyMismatchAndCategoryTypeMismatch() throws SQLException {
		assertInsertRejected(bob.getId(), euro.id(), bobCategory.id(), "EXPENSE", "EUR", "1.00", "23503");
		assertInsertRejected(alice.getId(), euro.id(), bobCategory.id(), "EXPENSE", "EUR", "1.00", "23503");
		assertInsertRejected(alice.getId(), euro.id(), expense.id(), "INCOME", "EUR", "1.00", "23503");
		assertInsertRejected(alice.getId(), euro.id(), expense.id(), "EXPENSE", "USD", "1.00", "23503");
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-0.01", "NaN"})
	void databaseCheckRejectsNonPositiveAndNonFiniteAmounts(String amount) throws SQLException {
		assertInsertRejected(alice.getId(), euro.id(), expense.id(), "EXPENSE", "EUR", amount, "23514");
	}

	@Test
	void databasePreventsDeletingHistoryAndChangingReferencedCurrencyOrType() throws SQLException {
		service.createTransaction(alice.getId(), form(euro.id(), expense.id(), CategoryType.EXPENSE, "1.00"));
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			for (String sql : List.of("DELETE FROM accounts WHERE id = " + euro.id(),
					"DELETE FROM categories WHERE id = " + expense.id(),
					"UPDATE accounts SET currency = 'USD' WHERE id = " + euro.id(),
					"UPDATE categories SET category_type = 'INCOME' WHERE id = " + expense.id())) {
				assertThatThrownBy(() -> statement.executeUpdate(sql)).isInstanceOf(SQLException.class)
						.extracting(exception -> ((SQLException) exception).getSQLState()).isEqualTo("23503");
			}
		}
	}

	private void assertInsertRejected(Long owner, Long account, Long category, String type, String currency,
			String amount, String sqlState) throws SQLException {
		try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("""
				INSERT INTO transactions (user_id, account_id, category_id, transaction_type, currency, amount, transaction_date)
				VALUES (?, ?, ?, ?, ?, CAST(? AS NUMERIC), CURRENT_DATE)
				""")) {
			statement.setLong(1, owner);
			statement.setLong(2, account);
			statement.setLong(3, category);
			statement.setString(4, type);
			statement.setString(5, currency);
			statement.setString(6, amount);
			assertThatThrownBy(statement::executeUpdate).isInstanceOf(SQLException.class)
					.extracting(exception -> ((SQLException) exception).getSQLState()).isEqualTo(sqlState);
		}
	}

	private void assertBalance(Long accountId, String expected) {
		assertThat(accounts.findAccountForUser(alice.getId(), accountId).currentBalance()).isEqualByComparingTo(expected);
		assertThat(accounts.findAccountsForUser(alice.getId())).filteredOn(account -> account.id().equals(accountId))
				.singleElement().extracting(AccountView::currentBalance).isEqualTo(new BigDecimal(expected));
	}

	private MockHttpServletRequestBuilder validRequest(String path, Long accountId, Long categoryId, String type, String amount) {
		return post(path).with(user(alicePrincipal)).with(csrf()).param("accountId", accountId.toString())
				.param("categoryId", categoryId.toString()).param("transactionType", type).param("amount", amount)
				.param("transactionDate", LocalDate.now().toString()).param("description", "  Test description  ");
	}

	private TransactionForm form(Long accountId, Long categoryId, CategoryType type, String amount) {
		TransactionForm form = new TransactionForm();
		form.setAccountId(accountId);
		form.setCategoryId(categoryId);
		form.setTransactionType(type);
		form.setAmount(new BigDecimal(amount));
		form.setTransactionDate(LocalDate.now());
		return form;
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

	private FinanceUserDetails principal(User user) {
		return new FinanceUserDetails(user.getId(), user.getName(), user.getEmail(), user.getPasswordHash());
	}
}
