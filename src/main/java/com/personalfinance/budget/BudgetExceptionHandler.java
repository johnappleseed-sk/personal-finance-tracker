package com.personalfinance.budget;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Renders safe 404/400 responses for budget routes. */
@ControllerAdvice(assignableTypes = BudgetController.class)
public class BudgetExceptionHandler {

	@ExceptionHandler(BudgetNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public String handleNotFound() {
		return "budgets/not-found";
	}

	@ExceptionHandler({MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class})
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public void handleBadId() {
	}
}
