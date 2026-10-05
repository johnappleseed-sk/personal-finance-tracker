package com.personalfinance.transaction;

/** Safe missing/foreign entry error with no financial information. */
public class TransactionNotFoundException extends RuntimeException {

	public TransactionNotFoundException() {
		super("Transaction not found.");
	}
}
