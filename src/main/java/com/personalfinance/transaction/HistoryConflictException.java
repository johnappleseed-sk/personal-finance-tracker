package com.personalfinance.transaction;

/** Blocks resource changes that would delete history or reinterpret existing ledger amounts. */
public class HistoryConflictException extends RuntimeException {

	/** @param message fixed, non-sensitive explanation suitable for the signed-in user */
	public HistoryConflictException(String message) {
		super(message);
	}
}
