package com.personalfinance.transaction;

/** Safe field feedback for unavailable references or a mismatching category type. */
public class TransactionInputException extends RuntimeException {

	private final String field;

	/**
	 * @param field allowlisted field
	 * @param message fixed non-sensitive validation message
	 */
	public TransactionInputException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String getField() {
		return field;
	}
}
