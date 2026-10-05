package com.personalfinance.account;

/** Treats missing and foreign accounts identically to avoid disclosing another user's data. */
public class AccountNotFoundException extends RuntimeException {

	/** Creates a safe error without embedding account identifiers or financial information. */
	public AccountNotFoundException() {
		super("Account not found.");
	}
}
