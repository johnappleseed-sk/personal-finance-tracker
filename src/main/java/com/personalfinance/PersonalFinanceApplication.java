package com.personalfinance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstraps the personal finance application and scans its feature packages.
 */
@SpringBootApplication
public class PersonalFinanceApplication {

	/**
	 * Starts the Spring application context and embedded web server.
	 *
	 * @param args command-line configuration overrides
	 */
	public static void main(String[] args) {
		SpringApplication.run(PersonalFinanceApplication.class, args);
	}

}
