package com.personalfinance.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrictBCryptPasswordEncoderTests {

	private final StrictBCryptPasswordEncoder encoder = new StrictBCryptPasswordEncoder();

	@Test
	void fullBoundaryPasswordsMatchButAppendedSuffixesDoNot() {
		for (String password : new String[] {"x".repeat(72), "é".repeat(36)}) {
			String hash = encoder.encode(password);
			assertThat(encoder.matches(password, hash)).isTrue();
			assertThat(encoder.matches(password + "x", hash)).isFalse();
		}
	}

	@Test
	void nullAndOversizedInputsCannotBeEncodedOrMatched() {
		for (String password : new String[] {null, "x".repeat(73), "é".repeat(37)}) {
			assertThatThrownBy(() -> encoder.encode(password)).isInstanceOf(IllegalArgumentException.class);
			assertThat(encoder.matches(password, "unused-test-hash")).isFalse();
		}
	}

	@Test
	void hashesAreSaltedAndPasswordsAreNotTrimmed() {
		String password = " Synthetic-test-passphrase ";
		String firstHash = encoder.encode(password);
		String secondHash = encoder.encode(password);
		assertThat(firstHash).isNotEqualTo(secondHash).startsWith("$2a$12$");
		assertThat(encoder.matches(password, firstHash)).isTrue();
		assertThat(encoder.matches(password.strip(), firstHash)).isFalse();
	}
}
