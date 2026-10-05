package com.personalfinance.category;

import com.personalfinance.user.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Gives missing and foreign categories the same safe response without revealing resource details. */
@ControllerAdvice(assignableTypes = CategoryController.class)
public class CategoryExceptionHandler {

	/** @return generic 404 page for unavailable categories or deleted session identities */
	@ExceptionHandler({CategoryNotFoundException.class, UserNotFoundException.class})
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public String resourceNotFound() {
		return "categories/not-found";
	}
}
