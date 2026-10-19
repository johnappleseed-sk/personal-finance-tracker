package com.personalfinance.transfer;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Renders safe 404/400 responses for transfer routes. */
@ControllerAdvice(assignableTypes = TransferController.class)
public class TransferExceptionHandler {

	@ExceptionHandler(TransferNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public String handleNotFound() {
		return "transfers/not-found";
	}

	@ExceptionHandler({MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class})
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public void handleBadId() {
	}
}
