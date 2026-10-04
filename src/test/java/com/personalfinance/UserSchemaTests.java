package com.personalfinance;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class UserSchemaTests {

	@Container
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private DataSource dataSource;

	@Test
	void usersHaveGeneratedIdsAndTimestampDefaults() throws SQLException {
		try (var connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.createStatement();
				 var result = statement.executeQuery("""
						INSERT INTO users (name, email, password_hash)
						VALUES ('Schema Test', 'schema@example.test', 'test-hash')
						RETURNING id, created_at, updated_at
						""")) {
				assertThat(result.next()).isTrue();
				assertThat(result.getLong("id")).isPositive();
				assertThat(result.getTimestamp("created_at")).isNotNull();
				assertThat(result.getTimestamp("updated_at")).isEqualTo(result.getTimestamp("created_at"));
			} finally {
				connection.rollback();
			}
		}
	}

	@Test
	void duplicateEmailIsRejected() throws SQLException {
		try (var connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.createStatement()) {
				String insert = """
						INSERT INTO users (name, email, password_hash)
						VALUES ('Schema Test', 'duplicate@example.test', 'test-hash')
						""";
				statement.executeUpdate(insert);
				assertThatThrownBy(() -> statement.executeUpdate(insert))
						.isInstanceOf(SQLException.class)
						.extracting(exception -> ((SQLException) exception).getSQLState())
						.isEqualTo("23505");
			} finally {
				connection.rollback();
			}
		}
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", "   ", "USER@example.test", " user@example.test "})
	void missingBlankOrNoncanonicalEmailIsRejected(String email) throws SQLException {
		assertInvalidUser("Schema Test", email, "test-hash", email == null ? "23502" : "23514");
	}

	@ParameterizedTest
	@CsvSource(value = {
			"<null>,test-hash,23502",
			"'',test-hash,23514",
			"Schema Test,<null>,23502",
			"Schema Test,'',23514"
	}, nullValues = "<null>")
	void missingOrBlankRequiredFieldsAreRejected(String name, String passwordHash, String sqlState)
			throws SQLException {
		assertInvalidUser(name, "required@example.test", passwordHash, sqlState);
	}

	private void assertInvalidUser(String name, String email, String passwordHash, String sqlState)
			throws SQLException {
		try (var connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var statement = connection.prepareStatement(
					"INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)")) {
				statement.setString(1, name);
				statement.setString(2, email);
				statement.setString(3, passwordHash);
				assertThatThrownBy(statement::executeUpdate)
						.isInstanceOf(SQLException.class)
						.extracting(exception -> ((SQLException) exception).getSQLState())
						.isEqualTo(sqlState);
			} finally {
				connection.rollback();
			}
		}
	}
}
