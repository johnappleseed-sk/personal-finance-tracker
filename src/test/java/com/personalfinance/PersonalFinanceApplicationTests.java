package com.personalfinance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PersonalFinanceApplicationTests {

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

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private Environment environment;

	@Autowired
	private Flyway flyway;

	@Test
	void contextLoads() {
		assertThat(entityManagerFactory.isOpen()).isTrue();
	}

	@Test
	void datasourceConnectsToPostgresql() throws SQLException {
		try (Connection connection = dataSource.getConnection();
			 var statement = connection.createStatement();
			 var result = statement.executeQuery("SELECT 1")) {
			assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
			assertThat(result.next()).isTrue();
			assertThat(result.getInt(1)).isEqualTo(1);
		}
	}

	@Test
	void postgresqlRejectsIncorrectCredentials() {
		assertThatThrownBy(() -> {
			try (Connection ignored = DriverManager.getConnection(
					postgres.getJdbcUrl(), postgres.getUsername(), "incorrect-test-password")) {
				// Closing protects the test if authentication unexpectedly succeeds.
			}
		}).isInstanceOf(SQLException.class)
			.extracting(exception -> ((SQLException) exception).getSQLState())
			.isEqualTo("28P01");
	}

	@Test
	void databaseConfigurationDoesNotGenerateSchemaOrUseOpenSessionInView() {
		assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
		assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
		assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
	}

	@Test
	void flywayAppliesInitialMigrationAndRecordsHistory() throws SQLException {
		assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
		assertThat(flyway.info().pending()).isEmpty();
		try (Connection connection = dataSource.getConnection();
			 var statement = connection.createStatement();
			 var result = statement.executeQuery(
					"SELECT script, success FROM flyway_schema_history WHERE version = '1'")) {
			assertThat(result.next()).isTrue();
			assertThat(result.getString("script")).isEqualTo("V1__create_users.sql");
			assertThat(result.getBoolean("success")).isTrue();
			assertThat(result.next()).isFalse();
		}
	}

	@Test
	void accountsMigrationUpgradesExistingUsersWithoutLosingData() throws SQLException {
		Flyway initial = Flyway.configure().dataSource(dataSource).schemas("upgrade_test")
				.locations("classpath:db/migration").target("1").cleanDisabled(true).load();
		assertThat(initial.migrate().migrationsExecuted).isEqualTo(1);
		try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.executeUpdate("""
					INSERT INTO upgrade_test.users (name, email, password_hash)
					VALUES ('Existing User', 'existing@example.test', 'synthetic-unused-hash')
					""");
			Flyway upgrade = flywayForSchema("upgrade_test", "classpath:db/migration");
			assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(2);
			assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("3");
			try (var result = statement.executeQuery("SELECT count(*) FROM upgrade_test.users")) {
				assertThat(result.next()).isTrue();
				assertThat(result.getInt(1)).isEqualTo(1);
			}
			statement.executeUpdate("""
					INSERT INTO upgrade_test.accounts (user_id, name, account_type, currency, initial_balance)
					SELECT id, 'Existing user account', 'CASH', 'EUR', 1.23 FROM upgrade_test.users
					""");
			assertThat(upgrade.migrate().migrationsExecuted).isZero();
		}
	}

	@Test
	void categoriesMigrationUpgradesExistingAccountsWithoutLosingData() throws SQLException {
		Flyway initial = Flyway.configure().dataSource(dataSource).schemas("categories_upgrade_test")
				.locations("classpath:db/migration").target("2").cleanDisabled(true).load();
		assertThat(initial.migrate().migrationsExecuted).isEqualTo(2);
		try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.executeUpdate("""
					INSERT INTO categories_upgrade_test.users (name, email, password_hash)
					VALUES ('Existing User', 'existing@example.test', 'synthetic-unused-hash')
					""");
			statement.executeUpdate("""
					INSERT INTO categories_upgrade_test.accounts (user_id, name, account_type, currency, initial_balance)
					SELECT id, 'Preserved account', 'CASH', 'EUR', -12.34 FROM categories_upgrade_test.users
					""");
			Flyway upgrade = flywayForSchema("categories_upgrade_test", "classpath:db/migration");
			assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
			try (var result = statement.executeQuery("SELECT name, initial_balance FROM categories_upgrade_test.accounts")) {
				assertThat(result.next()).isTrue();
				assertThat(result.getString("name")).isEqualTo("Preserved account");
				assertThat(result.getBigDecimal("initial_balance")).isEqualByComparingTo("-12.34");
				assertThat(result.next()).isFalse();
			}
			statement.executeUpdate("""
					INSERT INTO categories_upgrade_test.categories (user_id, name, category_type)
					SELECT id, 'Groceries', 'EXPENSE' FROM categories_upgrade_test.users
					""");
			assertThat(upgrade.migrate().migrationsExecuted).isZero();
		}
	}

	@Test
	void repeatedMigrationDoesNotReapplyChangesOrRemoveData() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try {
				try (var statement = connection.createStatement()) {
					statement.executeUpdate("""
							INSERT INTO users (name, email, password_hash)
							VALUES ('Migration Test', 'migration@example.test', 'test-hash-not-a-real-credential')
							""");
					connection.commit();
					assertThat(flyway.migrate().migrationsExecuted).isZero();
					try (var result = statement.executeQuery(
							"SELECT count(*) FROM users WHERE email = 'migration@example.test'")) {
						assertThat(result.next()).isTrue();
						assertThat(result.getInt(1)).isEqualTo(1);
					}
				}
			} finally {
				connection.rollback();
			}
		}
	}

	@Test
	void flywayRejectsChangedAppliedMigration() {
		Flyway original = flywayForSchema("checksum_test", "classpath:db/migration");
		original.migrate();
		Flyway changed = flywayForSchema("checksum_test", "classpath:db/changed-migration");
		assertThatThrownBy(changed::migrate)
				.isInstanceOf(FlywayValidateException.class)
				.hasMessageContaining("checksum mismatch");
	}

	@Test
	void flywayRefusesToBaselineAnUnmanagedNonemptySchema() throws SQLException {
		try (Connection connection = dataSource.getConnection();
			 var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA unmanaged_test");
			statement.execute("CREATE TABLE unmanaged_test.existing_data (id BIGINT PRIMARY KEY)");
		}
		assertThatThrownBy(flywayForSchema("unmanaged_test", "classpath:db/migration")::migrate)
				.isInstanceOf(FlywayException.class)
				.hasMessageContaining("non-empty schema");
	}

	@Test
	void destructiveFlywayCleanIsDisabled() {
		assertThatThrownBy(flyway::clean)
				.isInstanceOf(FlywayException.class)
				.hasMessageContaining("cleanDisabled");
	}

	private Flyway flywayForSchema(String schema, String location) {
		return Flyway.configure()
				.dataSource(dataSource)
				.schemas(schema)
				.locations(location)
				.validateOnMigrate(flyway.getConfiguration().isValidateOnMigrate())
				.baselineOnMigrate(flyway.getConfiguration().isBaselineOnMigrate())
				.cleanDisabled(flyway.getConfiguration().isCleanDisabled())
				.load();
	}

}
