package com.personalfinance.budget;

/** Reports a safe form field error during budget creation or update. */
public class BudgetInputException extends RuntimeException {

	private final String field;

	public BudgetInputException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String getField() {
		return field;
	}
}
