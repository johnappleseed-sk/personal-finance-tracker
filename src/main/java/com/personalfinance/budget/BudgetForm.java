package com.personalfinance.budget;

import java.math.BigDecimal;

import com.personalfinance.account.AccountCurrency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** Validated form inputs for creating or editing a monthly expense budget. */
public class BudgetForm {

	@NotNull(message = "Choose an expense category.")
	@Positive(message = "Choose an available expense category.")
	private Long categoryId;

	@NotNull(message = "Choose EUR, USD, or GBP.")
	private AccountCurrency currency;

	@NotBlank(message = "Select a budget month in YYYY-MM format.")
	@Pattern(regexp = "^[0-9]{4}-(0[1-9]|1[0-2])$", message = "Select a valid month in YYYY-MM format.")
	private String budgetMonth;

	@NotNull(message = "Enter a monthly spending limit.")
	@DecimalMin(value = "0.01", message = "Enter a positive limit of at least 0.01.")
	@Digits(integer = 17, fraction = 2, message = "Use at most 17 whole-number digits and 2 decimal places.")
	private BigDecimal limitAmount;

	public static BudgetForm from(BudgetView view) {
		BudgetForm form = new BudgetForm();
		form.setCategoryId(view.categoryId());
		form.setCurrency(view.currency());
		form.setBudgetMonth(view.budgetMonth());
		form.setLimitAmount(view.limitAmount());
		return form;
	}

	public Long getCategoryId() { return categoryId; }
	public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
	public AccountCurrency getCurrency() { return currency; }
	public void setCurrency(AccountCurrency currency) { this.currency = currency; }
	public String getBudgetMonth() { return budgetMonth; }
	public void setBudgetMonth(String budgetMonth) { this.budgetMonth = budgetMonth == null ? null : budgetMonth.strip(); }
	public BigDecimal getLimitAmount() { return limitAmount; }
	public void setLimitAmount(BigDecimal limitAmount) { this.limitAmount = limitAmount; }
}
