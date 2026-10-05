package com.personalfinance.account;

import com.personalfinance.user.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Converts missing/foreign account access into identical safe HTML responses without leaking resource details. */
@ControllerAdvice(assignableTypes = AccountController.class)
public class AccountExceptionHandler {

	/** @return generic 404 page for missing resources or unavailable session identities */
	@ExceptionHandler({AccountNotFoundException.class, UserNotFoundException.class})
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public String resourceNotFound() {
		return "accounts/not-found";
	}
}
