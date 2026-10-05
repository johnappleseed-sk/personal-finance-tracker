package com.personalfinance.account;

/** Supported account kinds; stored as stable enum names rather than ordinal numbers. */
public enum AccountType {

	CHECKING("Checking"),
	SAVINGS("Savings"),
	CASH("Cash"),
	CREDIT_CARD("Credit card");

	private final String label;

	AccountType(String label) {
		this.label = label;
	}

	/** @return readable label without putting presentation logic in templates */
	public String getLabel() {
		return label;
	}
}
