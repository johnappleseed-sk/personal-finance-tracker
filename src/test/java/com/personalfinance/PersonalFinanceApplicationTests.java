package com.personalfinance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;
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

}
