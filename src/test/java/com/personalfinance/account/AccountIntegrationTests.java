package com.personalfinance.account;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import com.personalfinance.security.FinanceUserDetails;
import com.personalfinance.user.User;
import com.personalfinance.user.UserNotFoundException;
import com.personalfinance.user.UserRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
class AccountIntegrationTests {

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private AccountService service;
	@Autowired
	private AccountRepository repository;
	@Autowired
	private UserRepository users;
	@Autowired
	private DataSource dataSource;

	private User alice;
	private User bob;
	private FinanceUserDetails alicePrincipal;
	private FinanceUserDetails bobPrincipal;

	@BeforeEach
	void setUpIsolatedUsers() {
		repository.deleteAll();
		users.deleteAll();
		alice = users.saveAndFlush(new User("Alice", "alice@example.test", "synthetic-unused-hash"));
		bob = users.saveAndFlush(new User("Bob", "bob@example.test", "synthetic-unused-hash"));
		alicePrincipal = principal(alice);
		bobPrincipal = principal(bob);
	}

	@Test
	void emptyStateAndCreationFormAreAccessibleOnlyAfterAuthentication() throws Exception {
		for (String path : List.of("/accounts", "/accounts/new", "/accounts/1/edit", "/accounts/1/delete")) {
			mvc.perform(get(path)).andExpect(redirectedUrl("/login"));
		}
		mvc.perform(get("/accounts").with(user(alicePrincipal)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("No accounts yet.")));
		mvc.perform(get("/accounts/new").with(user(alicePrincipal)))
				.andExpect(status().isOk()).andExpect(view().name("accounts/form"))
				.andExpect(content().string(containsString("name=\"_csrf\"")))
				.andExpect(content().string(containsString("name=\"viewport\"")))
				.andExpect(content().string(containsString("for=\"initialBalance\"")));
	}

	@Test
	void creationUsesPrincipalOwnershipNotSubmittedIdsAndStoresExactMoney() throws Exception {
		mvc.perform(validRequest("/accounts", "  Main account  ", "1234.56")
				.param("userId", bob.getId().toString()).param("user.id", bob.getId().toString())
				.param("id", "999999").param("createdAt", "2000-01-01T00:00:00Z"))
				.andExpect(redirectedUrl("/accounts")).andExpect(flash().attribute("successMessage", "Account created."));
		AccountView created = service.findAccountsForUser(alice.getId()).getFirst();
		assertThat(created.name()).isEqualTo("Main account");
		assertThat(created.initialBalance()).isEqualByComparingTo("1234.56");
		assertThat(created.initialBalance().scale()).isEqualTo(2);
		assertThat(created.id()).isNotEqualTo(999999L);
		assertThat(service.findAccountsForUser(bob.getId())).isEmpty();
		Account stored = repository.findByIdAndUserId(created.id(), alice.getId()).orElseThrow();
		assertThat(stored.getCreatedAt()).isNotNull();
		assertThat(stored.getUpdatedAt()).isEqualTo(stored.getCreatedAt());
		assertThat(repository.findByIdAndUserId(created.id(), bob.getId())).isEmpty();
	}

	@Test
	void listIsScopedSortedEscapedAndDoesNotMixCurrencies() throws Exception {
		service.createAccount(alice.getId(), form("Zeta", "10.00"));
		AccountForm euro = form("<script>alpha</script>", "-20.01");
		service.createAccount(alice.getId(), euro);
		AccountForm dollars = form("Dollar account", "20.00");
		dollars.setCurrency(AccountCurrency.USD);
		service.createAccount(alice.getId(), dollars);
		service.createAccount(bob.getId(), form("Bob private account", "999.99"));
		assertThat(service.findAccountsForUser(alice.getId())).extracting(AccountView::name)
				.containsExactly("Dollar account", "<script>alpha</script>", "Zeta");
		mvc.perform(get("/accounts").with(user(alicePrincipal)).param("userId", bob.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("&lt;script&gt;alpha&lt;/script&gt;")))
				.andExpect(content().string(not(containsString("<script>"))))
				.andExpect(content().string(not(containsString("Bob private account"))))
				.andExpect(content().string(containsString("-20.01")))
				.andExpect(content().string(containsString("USD")));
	}

	@Test
	void ownerCanEditWithoutChangingOwnershipAndTimestampsAreMaintained() throws Exception {
		AccountView account = service.createAccount(alice.getId(), form("Original", "0.10"));
		Account before = repository.findByIdAndUserId(account.id(), alice.getId()).orElseThrow();
		mvc.perform(get("/accounts/{id}/edit", account.id()).with(user(alicePrincipal)))
				.andExpect(status().isOk()).andExpect(model().attributeExists("accountForm"));
		mvc.perform(validRequest("/accounts/" + account.id(), "Updated", "-12.34")
				.param("userId", bob.getId().toString()).param("user.id", bob.getId().toString()))
				.andExpect(redirectedUrl("/accounts"));
		Account after = repository.findByIdAndUserId(account.id(), alice.getId()).orElseThrow();
		assertThat(after.getName()).isEqualTo("Updated");
		assertThat(after.getInitialBalance()).isEqualByComparingTo("-12.34");
		assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
		assertThat(after.getUpdatedAt()).isAfterOrEqualTo(before.getUpdatedAt());
		assertThat(repository.findByIdAndUserId(account.id(), bob.getId())).isEmpty();
	}

	@Test
	void foreignAndMissingAccountsReturnIdenticalSafe404ForEveryOperation() throws Exception {
		AccountView account = service.createAccount(alice.getId(), form("Alice private", "9876.54"));
		for (Long id : List.of(account.id(), Long.MAX_VALUE)) {
			for (String suffix : List.of("/edit", "/delete")) {
				mvc.perform(get("/accounts/" + id + suffix).with(user(bobPrincipal)))
						.andExpect(status().isNotFound()).andExpect(view().name("accounts/not-found"))
						.andExpect(content().string(not(containsString("Alice private"))))
						.andExpect(content().string(not(containsString("9876.54"))));
			}
			mvc.perform(post("/accounts/" + id).with(user(bobPrincipal)).with(csrf())
					.param("name", "Hijacked").param("accountType", "CASH").param("initialBalance", "1.00").param("currency", "EUR"))
					.andExpect(status().isNotFound());
			// Even invalid input must not turn a foreign edit into an accessible form.
			mvc.perform(post("/accounts/" + id).with(user(bobPrincipal)).with(csrf()).param("name", ""))
					.andExpect(status().isNotFound());
			mvc.perform(post("/accounts/" + id + "/delete").with(user(bobPrincipal)).with(csrf()))
					.andExpect(status().isNotFound());
		}
		assertThat(service.findAccountForUser(alice.getId(), account.id()).name()).isEqualTo("Alice private");
		assertThat(repository.count()).isEqualTo(1);
	}

	@Test
	void serviceOwnershipChecksProtectNonMvcCallers() {
		AccountView account = service.createAccount(alice.getId(), form("Private", "1.00"));
		assertThatThrownBy(() -> service.findAccountForUser(bob.getId(), account.id())).isInstanceOf(AccountNotFoundException.class);
		assertThatThrownBy(() -> service.updateAccount(bob.getId(), account.id(), form("Other", "1.00")))
				.isInstanceOf(AccountNotFoundException.class);
		assertThatThrownBy(() -> service.deleteAccount(bob.getId(), account.id())).isInstanceOf(AccountNotFoundException.class);
		assertThat(repository.count()).isEqualTo(1);
	}

	@Test
	void deleteConfirmationDoesNotDeleteAndOnlyValidPostCanDelete() throws Exception {
		AccountView account = service.createAccount(alice.getId(), form("Delete me", "0.00"));
		mvc.perform(get("/accounts/{id}/delete", account.id()).with(user(alicePrincipal)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("cannot be undone")));
		assertThat(repository.count()).isEqualTo(1);
		mvc.perform(post("/accounts/{id}/delete", account.id()).with(user(alicePrincipal)))
				.andExpect(status().isForbidden());
		mvc.perform(post("/accounts/{id}/delete", account.id()).with(user(alicePrincipal)).with(csrf().useInvalidToken()))
				.andExpect(status().isForbidden());
		assertThat(repository.count()).isEqualTo(1);
		mvc.perform(post("/accounts/{id}/delete", account.id()).with(user(alicePrincipal)).with(csrf()))
				.andExpect(redirectedUrl("/accounts")).andExpect(flash().attribute("successMessage", "Account deleted."));
		assertThat(repository.count()).isZero();
	}

	@Test
	void allMutationsRequireAuthenticationAndCsrf() throws Exception {
		AccountView account = service.createAccount(alice.getId(), form("Protected", "1.00"));
		for (String path : List.of("/accounts", "/accounts/" + account.id(), "/accounts/" + account.id() + "/delete")) {
			mvc.perform(post(path).with(csrf())).andExpect(redirectedUrl("/login"));
			mvc.perform(post(path).with(user(alicePrincipal))).andExpect(status().isForbidden());
			mvc.perform(post(path).with(user(alicePrincipal)).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
		}
		assertThat(repository.count()).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.001", "-1.001", "100000000000000000.00", "not-money", "NaN", "Infinity", ""})
	void invalidMonetaryInputIsRejectedWithoutRounding(String balance) throws Exception {
		mvc.perform(validRequest("/accounts", "Invalid amount", balance))
				.andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("accountForm", "initialBalance"));
		assertThat(repository.count()).isZero();
	}

	@Test
	void missingFieldsUnsupportedEnumsAndBlankNamesAreRejected() throws Exception {
		mvc.perform(post("/accounts").with(user(alicePrincipal)).with(csrf()))
				.andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("accountForm",
						"name", "accountType", "initialBalance", "currency"));
		mvc.perform(post("/accounts").with(user(alicePrincipal)).with(csrf()).param("name", " ")
				.param("accountType", "UNKNOWN").param("initialBalance", "0.00").param("currency", "JPY"))
				.andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("accountForm", "name", "accountType", "currency"))
				.andExpect(content().string(containsString("Choose a supported account type.")))
				.andExpect(content().string(containsString("Choose EUR, USD, or GBP.")));
		mvc.perform(validRequest("/accounts", "n".repeat(101), "0.00"))
				.andExpect(model().attributeHasFieldErrors("accountForm", "name"));
		assertThat(repository.count()).isZero();
	}

	@Test
	void invalidEditKeepsSavedDataAndPreservesFormValues() throws Exception {
		AccountView account = service.createAccount(alice.getId(), form("Unchanged", "12.34"));
		mvc.perform(validRequest("/accounts/" + account.id(), "Submitted name", "1.001"))
				.andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("accountForm", "initialBalance"))
				.andExpect(content().string(containsString("Submitted name")));
		AccountView stored = service.findAccountForUser(alice.getId(), account.id());
		assertThat(stored.name()).isEqualTo("Unchanged");
		assertThat(stored.initialBalance()).isEqualByComparingTo("12.34");
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-0.01", "99999999999999999.99", "-99999999999999999.99"})
	void supportedBalancesPersistExactlyAtBoundaries(String balance) {
		AccountView account = service.createAccount(alice.getId(), form("Boundary", balance));
		assertThat(account.initialBalance()).isEqualByComparingTo(balance);
		assertThat(service.findAccountForUser(alice.getId(), account.id()).initialBalance()).isEqualByComparingTo(balance);
	}

	@Test
	void serviceValidationRejectsNullInvalidMoneyAndUnavailableIdentity() {
		assertThatThrownBy(() -> service.createAccount(alice.getId(), form("Invalid", "1.001")))
				.isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> service.createAccount(alice.getId(), null)).isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> service.findAccountForUser(alice.getId(), -1L)).isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> service.createAccount(Long.MAX_VALUE, form("Unavailable", "0.00")))
				.isInstanceOf(UserNotFoundException.class);
		assertThat(repository.count()).isZero();
	}

	@ParameterizedTest
	@CsvSource({"CHECKING,EUR", "SAVINGS,USD", "CASH,GBP", "CREDIT_CARD,EUR"})
	void everySupportedAccountTypeAndCurrencyCanBeSaved(AccountType type, AccountCurrency currency) {
		AccountForm form = form("Supported", "-1.00");
		form.setAccountType(type);
		form.setCurrency(currency);
		AccountView account = service.createAccount(alice.getId(), form);
		assertThat(account.accountType()).isEqualTo(type);
		assertThat(account.currency()).isEqualTo(currency);
	}

	@ParameterizedTest
	@CsvSource({"'',CHECKING,EUR,0.00,23514", "Name,INVALID,EUR,0.00,23514",
			"Name,CASH,JPY,0.00,23514", "Name,CASH,EUR,NaN,23514"})
	void databaseConstraintsRejectInvalidAccountData(String name, String type, String currency,
			String balance, String sqlState) throws SQLException {
		try (var connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.prepareStatement("""
					INSERT INTO accounts (user_id, name, account_type, currency, initial_balance)
					VALUES (?, ?, ?, ?, CAST(? AS NUMERIC))
					""")) {
				statement.setLong(1, alice.getId());
				statement.setString(2, name);
				statement.setString(3, type);
				statement.setString(4, currency);
				statement.setString(5, balance);
				assertThatThrownBy(statement::executeUpdate).isInstanceOf(SQLException.class)
						.extracting(exception -> ((SQLException) exception).getSQLState()).isEqualTo(sqlState);
			} finally {
				connection.rollback();
			}
		}
	}

	@Test
	void databaseForeignKeyRejectsMissingOwner() throws SQLException {
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			assertThatThrownBy(() -> statement.executeUpdate("""
					INSERT INTO accounts (user_id, name, account_type, currency, initial_balance)
					VALUES (9223372036854775807, 'Missing owner', 'CASH', 'EUR', 0.00)
					"""))
					.isInstanceOf(SQLException.class)
					.extracting(exception -> ((SQLException) exception).getSQLState()).isEqualTo("23503");
		}
	}

	private MockHttpServletRequestBuilder validRequest(String path, String name, String balance) {
		return post(path).with(user(alicePrincipal)).with(csrf()).param("name", name)
				.param("accountType", "CHECKING").param("initialBalance", balance).param("currency", "EUR");
	}

	private AccountForm form(String name, String balance) {
		AccountForm form = new AccountForm();
		form.setName(name);
		form.setAccountType(AccountType.CHECKING);
		form.setInitialBalance(new BigDecimal(balance));
		form.setCurrency(AccountCurrency.EUR);
		return form;
	}

	private FinanceUserDetails principal(User user) {
		return new FinanceUserDetails(user.getId(), user.getName(), user.getEmail(), user.getPasswordHash());
	}
}
