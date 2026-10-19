package com.personalfinance.transfer;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

/** Validated form inputs for transferring funds between two owned accounts. */
public class TransferForm {

	@NotNull(message = "Choose a source account.")
	@Positive(message = "Choose an available source account.")
	private Long sourceAccountId;

	@NotNull(message = "Choose a target account.")
	@Positive(message = "Choose an available target account.")
	private Long targetAccountId;

	@NotNull(message = "Enter an amount.")
	@DecimalMin(value = "0.01", message = "Enter a positive amount of at least 0.01.")
	@Digits(integer = 17, fraction = 2, message = "Use at most 17 whole-number digits and 2 decimal places.")
	private BigDecimal amount;

	@NotNull(message = "Enter a transfer date.")
	@PastOrPresent(message = "Choose today or an earlier date.")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate transferDate;

	@Size(max = 255, message = "Use at most 255 characters for the description.")
	private String description = "";

	public Long getSourceAccountId() { return sourceAccountId; }
	public void setSourceAccountId(Long sourceAccountId) { this.sourceAccountId = sourceAccountId; }
	public Long getTargetAccountId() { return targetAccountId; }
	public void setTargetAccountId(Long targetAccountId) { this.targetAccountId = targetAccountId; }
	public BigDecimal getAmount() { return amount; }
	public void setAmount(BigDecimal amount) { this.amount = amount; }
	public LocalDate getTransferDate() { return transferDate; }
	public void setTransferDate(LocalDate transferDate) { this.transferDate = transferDate; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description == null ? "" : description.strip(); }
}
