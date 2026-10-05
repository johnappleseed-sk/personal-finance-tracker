package com.personalfinance.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.personalfinance.category.CategoryType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

/** Editable ledger inputs only. Related IDs are untrusted and must be ownership-checked by the service. */
public class TransactionForm {

	@NotNull(message = "Choose an account.")
	@Positive(message = "Choose an available account.")
	private Long accountId;

	@NotNull(message = "Choose a category.")
	@Positive(message = "Choose an available category.")
	private Long categoryId;

	@NotNull(message = "Choose Income or Expense.")
	private CategoryType transactionType;

	@NotNull(message = "Enter an amount.")
	@DecimalMin(value = "0.01", message = "Enter a positive amount of at least 0.01.")
	@Digits(integer = 17, fraction = 2, message = "Use at most 17 whole-number digits and 2 decimal places.")
	private BigDecimal amount;

	@NotNull(message = "Enter a transaction date.")
	@PastOrPresent(message = "Choose today or an earlier date.")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate transactionDate;

	@Size(max = 255, message = "Use at most 255 characters for the description.")
	private String description = "";

	/** @return editable fields copied from an already-authorized transaction */
	public static TransactionForm from(TransactionView transaction) {
		TransactionForm form = new TransactionForm();
		form.setAccountId(transaction.accountId());
		form.setCategoryId(transaction.categoryId());
		form.setTransactionType(transaction.transactionType());
		form.setAmount(transaction.amount());
		form.setTransactionDate(transaction.transactionDate());
		form.setDescription(transaction.description());
		return form;
	}

	public Long getAccountId() { return accountId; }
	public void setAccountId(Long accountId) { this.accountId = accountId; }
	public Long getCategoryId() { return categoryId; }
	public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
	public CategoryType getTransactionType() { return transactionType; }
	public void setTransactionType(CategoryType transactionType) { this.transactionType = transactionType; }
	public BigDecimal getAmount() { return amount; }
	public void setAmount(BigDecimal amount) { this.amount = amount; }
	public LocalDate getTransactionDate() { return transactionDate; }
	public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description == null ? "" : description.strip(); }
}
