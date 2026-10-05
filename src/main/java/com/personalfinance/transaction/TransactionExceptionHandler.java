package com.personalfinance.transaction;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Treats missing and foreign transaction IDs identically without leaking ledger details. */
@ControllerAdvice(assignableTypes = TransactionController.class)
public class TransactionExceptionHandler {

	/** @return safe HTML 404 page */
	@ExceptionHandler(TransactionNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public String resourceNotFound() {
		return "transactions/not-found";
	}
}
