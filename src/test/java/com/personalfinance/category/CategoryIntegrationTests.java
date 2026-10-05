package com.personalfinance.category;

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
import org.junit.jupiter.params.provider.EnumSource;
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
class CategoryIntegrationTests {

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
	private CategoryService service;
	@Autowired
	private CategoryRepository repository;
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
	void pagesRequireAuthenticationAndHaveAccessibleEmptyStateAndForm() throws Exception {
		for (String path : List.of("/categories", "/categories/new", "/categories/1/edit", "/categories/1/delete")) {
			mvc.perform(get(path)).andExpect(redirectedUrl("/login"));
		}
		mvc.perform(get("/categories").with(user(alicePrincipal)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("No categories yet.")));
		mvc.perform(get("/categories/new").with(user(alicePrincipal)))
				.andExpect(status().isOk()).andExpect(view().name("categories/form"))
				.andExpect(content().string(containsString("name=\"_csrf\"")))
				.andExpect(content().string(containsString("for=\"categoryType\"")))
				.andExpect(content().string(containsString("name=\"viewport\"")));
	}

	@ParameterizedTest
	@EnumSource(CategoryType.class)
	void createsEveryTypeWithPrincipalOwnerAndIgnoresForgedFields(CategoryType type) throws Exception {
		mvc.perform(validRequest("/categories", "  Salary  ", type.name())
				.param("id", "999999").param("userId", bob.getId().toString()).param("user.id", bob.getId().toString())
				.param("createdAt", "2000-01-01T00:00:00Z"))
				.andExpect(redirectedUrl("/categories")).andExpect(flash().attribute("successMessage", "Category created."));
		CategoryView created = service.findCategoriesForUser(alice.getId()).getFirst();
		assertThat(created.name()).isEqualTo("Salary");
		assertThat(created.categoryType()).isEqualTo(type);
		assertThat(created.id()).isNotEqualTo(999999L);
		assertThat(service.findCategoriesForUser(bob.getId())).isEmpty();
		Category saved = repository.findByIdAndUserId(created.id(), alice.getId()).orElseThrow();
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getCreatedAt()).isEqualTo(saved.getUpdatedAt());
		assertThat(repository.findByIdAndUserId(created.id(), bob.getId())).isEmpty();
	}

	@Test
	void listsOnlyOwnedCategoriesInStableOrderAndAllowsDuplicateNames() throws Exception {
		service.createCategory(alice.getId(), form("Zeta", CategoryType.EXPENSE));
		CategoryView first = service.createCategory(alice.getId(), form("Alpha", CategoryType.INCOME));
		CategoryView second = service.createCategory(alice.getId(), form("Alpha", CategoryType.EXPENSE));
		service.createCategory(bob.getId(), form("Bob private", CategoryType.EXPENSE));
		List<CategoryView> categories = service.findCategoriesForUser(alice.getId());
		assertThat(categories).extracting(CategoryView::name).containsExactly("Alpha", "Alpha", "Zeta");
		assertThat(categories.get(0).id()).isEqualTo(first.id());
		assertThat(categories.get(1).id()).isEqualTo(second.id());
		mvc.perform(get("/categories").with(user(alicePrincipal)).param("userId", bob.getId().toString()))
				.andExpect(status().isOk()).andExpect(content().string(not(containsString("Bob private"))))
				.andExpect(content().string(not(containsString("synthetic-unused-hash"))))
				.andExpect(content().string(containsString("Income"))).andExpect(content().string(containsString("Expense")));
	}

	@Test
	void categoryNamesAreEscapedInEveryPage() throws Exception {
		CategoryView category = service.createCategory(alice.getId(), form("<script>alert(1)</script>", CategoryType.EXPENSE));
		for (String path : List.of("/categories", "/categories/" + category.id() + "/edit", "/categories/" + category.id() + "/delete")) {
			mvc.perform(get(path).with(user(alicePrincipal))).andExpect(status().isOk())
					.andExpect(content().string(containsString("&lt;script&gt;")))
					.andExpect(content().string(not(containsString("<script>"))));
		}
	}

	@Test
	void ownerCanEditNameAndTypeWithoutReassigningOwnerOrCreationTimestamp() throws Exception {
		CategoryView category = service.createCategory(alice.getId(), form("Original", CategoryType.EXPENSE));
		Category before = repository.findByIdAndUserId(category.id(), alice.getId()).orElseThrow();
		mvc.perform(validRequest("/categories/" + category.id(), "  Updated  ", "INCOME")
				.param("userId", bob.getId().toString()).param("user.id", bob.getId().toString()))
				.andExpect(redirectedUrl("/categories")).andExpect(flash().attribute("successMessage", "Category updated."));
		Category after = repository.findByIdAndUserId(category.id(), alice.getId()).orElseThrow();
		assertThat(after.getName()).isEqualTo("Updated");
		assertThat(after.getCategoryType()).isEqualTo(CategoryType.INCOME);
		assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
		assertThat(after.getUpdatedAt()).isAfterOrEqualTo(before.getUpdatedAt());
		assertThat(repository.findByIdAndUserId(category.id(), bob.getId())).isEmpty();
	}

	@Test
	void missingAndForeignCategoriesHaveIdenticalSafe404ForAllOperations() throws Exception {
		CategoryView category = service.createCategory(alice.getId(), form("Alice private", CategoryType.EXPENSE));
		for (Long id : List.of(category.id(), Long.MAX_VALUE)) {
			for (String suffix : List.of("/edit", "/delete")) {
				mvc.perform(get("/categories/" + id + suffix).with(user(bobPrincipal)))
						.andExpect(status().isNotFound()).andExpect(view().name("categories/not-found"))
						.andExpect(content().string(not(containsString("Alice private"))));
			}
			mvc.perform(post("/categories/" + id).with(user(bobPrincipal)).with(csrf())
					.param("name", "Hijacked").param("categoryType", "INCOME")).andExpect(status().isNotFound());
			mvc.perform(post("/categories/" + id).with(user(bobPrincipal)).with(csrf()).param("name", ""))
					.andExpect(status().isNotFound());
			mvc.perform(post("/categories/" + id + "/delete").with(user(bobPrincipal)).with(csrf()))
					.andExpect(status().isNotFound());
		}
		assertThat(service.findCategoryForUser(alice.getId(), category.id()).name()).isEqualTo("Alice private");
	}

	@Test
	void serviceOwnershipChecksProtectNonMvcCallers() {
		CategoryView category = service.createCategory(alice.getId(), form("Private", CategoryType.INCOME));
		assertThatThrownBy(() -> service.findCategoryForUser(bob.getId(), category.id())).isInstanceOf(CategoryNotFoundException.class);
		assertThatThrownBy(() -> service.updateCategory(bob.getId(), category.id(), form("Other", CategoryType.EXPENSE)))
				.isInstanceOf(CategoryNotFoundException.class);
		assertThatThrownBy(() -> service.deleteCategory(bob.getId(), category.id())).isInstanceOf(CategoryNotFoundException.class);
		assertThat(repository.count()).isEqualTo(1);
	}

	@Test
	void everyMutationRequiresAuthenticationAndCsrf() throws Exception {
		CategoryView category = service.createCategory(alice.getId(), form("Protected", CategoryType.EXPENSE));
		for (String path : List.of("/categories", "/categories/" + category.id(), "/categories/" + category.id() + "/delete")) {
			mvc.perform(post(path).with(csrf())).andExpect(redirectedUrl("/login"));
			mvc.perform(post(path).with(user(alicePrincipal))).andExpect(status().isForbidden());
			mvc.perform(post(path).with(user(alicePrincipal)).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
		}
		assertThat(repository.count()).isEqualTo(1);
	}

	@Test
	void confirmationIsReadOnlyAndOwnerCanDeleteWithValidPost() throws Exception {
		CategoryView category = service.createCategory(alice.getId(), form("Delete me", CategoryType.EXPENSE));
		mvc.perform(get("/categories/{id}/delete", category.id()).with(user(alicePrincipal)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("cannot be undone")));
		assertThat(repository.count()).isEqualTo(1);
		mvc.perform(post("/categories/{id}/delete", category.id()).with(user(alicePrincipal)).with(csrf()))
				.andExpect(redirectedUrl("/categories")).andExpect(flash().attribute("successMessage", "Category deleted."));
		assertThat(repository.count()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   ", "\u2003"})
	void blankNamesAreRejected(String name) throws Exception {
		mvc.perform(validRequest("/categories", name, "EXPENSE"))
				.andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("categoryForm", "name"));
		assertThat(repository.count()).isZero();
	}

	@Test
	void missingFieldsUnsupportedTypeAndOverlongNameAreRejected() throws Exception {
		mvc.perform(post("/categories").with(user(alicePrincipal)).with(csrf()))
				.andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("categoryForm", "name", "categoryType"));
		mvc.perform(validRequest("/categories", "Unsupported", "TRANSFER"))
				.andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("categoryForm", "categoryType"))
				.andExpect(content().string(containsString("Choose Income or Expense.")));
		mvc.perform(validRequest("/categories", "x".repeat(101), "EXPENSE"))
				.andExpect(model().attributeHasFieldErrors("categoryForm", "name"));
		assertThat(repository.count()).isZero();
		assertThat(service.createCategory(alice.getId(), form("x".repeat(100), CategoryType.EXPENSE)).name()).hasSize(100);
	}

	@Test
	void invalidEditPreservesSubmittedFieldsButDoesNotChangeDatabase() throws Exception {
		CategoryView category = service.createCategory(alice.getId(), form("Original", CategoryType.INCOME));
		mvc.perform(validRequest("/categories/" + category.id(), "Submitted", "UNKNOWN"))
				.andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("categoryForm", "categoryType"))
				.andExpect(content().string(containsString("Submitted")));
		assertThat(service.findCategoryForUser(alice.getId(), category.id())).isEqualTo(category);
	}

	@Test
	void serviceValidationAndDeletedIdentityAreHandledSafely() throws Exception {
		assertThatThrownBy(() -> service.createCategory(alice.getId(), form("", CategoryType.EXPENSE)))
				.isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> service.createCategory(alice.getId(), form("Valid", null)))
				.isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> service.createCategory(alice.getId(), null)).isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> service.findCategoriesForUser(null)).isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> service.findCategoryForUser(alice.getId(), -1L)).isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> service.createCategory(Long.MAX_VALUE, form("Unavailable", CategoryType.EXPENSE)))
				.isInstanceOf(UserNotFoundException.class);
		users.delete(alice);
		mvc.perform(validRequest("/categories", "Stale session", "EXPENSE")).andExpect(status().isNotFound());
		assertThat(repository.count()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-1", "not-a-number", "9223372036854775808"})
	void invalidPathIdsReturnBadRequest(String id) throws Exception {
		mvc.perform(get("/categories/" + id + "/edit").with(user(alicePrincipal))).andExpect(status().isBadRequest());
		mvc.perform(validRequest("/categories/" + id, "Valid", "EXPENSE")).andExpect(status().isBadRequest());
		mvc.perform(post("/categories/" + id + "/delete").with(user(alicePrincipal)).with(csrf()))
				.andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@CsvSource({"'',EXPENSE,23514", "' ',INCOME,23514", "Name,TRANSFER,23514", "NULL,EXPENSE,23502", "Name,NULL,23502"})
	void databaseRejectsInvalidData(String name, String type, String sqlState) throws SQLException {
		try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("""
				INSERT INTO categories (user_id, name, category_type) VALUES (?, ?, ?)
				""")) {
			statement.setLong(1, alice.getId());
			statement.setString(2, "NULL".equals(name) ? null : name);
			statement.setString(3, "NULL".equals(type) ? null : type);
			assertThatThrownBy(statement::executeUpdate).isInstanceOf(SQLException.class)
					.extracting(exception -> ((SQLException) exception).getSQLState()).isEqualTo(sqlState);
		}
	}

	@Test
	void databaseForeignKeyRejectsMissingOwnerAndOwnerDeletion() throws SQLException {
		service.createCategory(alice.getId(), form("Private", CategoryType.EXPENSE));
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			assertThatThrownBy(() -> statement.executeUpdate("""
					INSERT INTO categories (user_id, name, category_type)
					VALUES (9223372036854775807, 'Missing owner', 'EXPENSE')
					"""))
					.isInstanceOf(SQLException.class)
					.extracting(exception -> ((SQLException) exception).getSQLState()).isEqualTo("23503");
			assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM users WHERE id = " + alice.getId()))
					.isInstanceOf(SQLException.class)
					.extracting(exception -> ((SQLException) exception).getSQLState()).isEqualTo("23503");
		}
	}

	private MockHttpServletRequestBuilder validRequest(String path, String name, String type) {
		return post(path).with(user(alicePrincipal)).with(csrf()).param("name", name).param("categoryType", type);
	}

	private CategoryForm form(String name, CategoryType type) {
		CategoryForm form = new CategoryForm();
		form.setName(name);
		form.setCategoryType(type);
		return form;
	}

	private FinanceUserDetails principal(User user) {
		return new FinanceUserDetails(user.getId(), user.getName(), user.getEmail(), user.getPasswordHash());
	}
}
