package com.personalfinance.transaction;

import com.personalfinance.account.AccountController;
import com.personalfinance.category.CategoryController;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Renders safe, actionable conflict feedback rather than exposing database foreign-key errors. */
@ControllerAdvice(assignableTypes = {AccountController.class, CategoryController.class})
public class HistoryExceptionHandler {

	/** @return non-sensitive explanation for a history-protected account/category mutation */
	@ExceptionHandler(HistoryConflictException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public String historyConflict(HistoryConflictException exception, Model model) {
		model.addAttribute("message", exception.getMessage());
		return "transactions/history-conflict";
	}
}
