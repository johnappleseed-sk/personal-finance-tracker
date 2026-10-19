package com.personalfinance.transfer;

/** Reports a safe form field error during transfer creation. */
public class TransferInputException extends RuntimeException {

	private final String field;

	public TransferInputException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String getField() {
		return field;
	}
}
