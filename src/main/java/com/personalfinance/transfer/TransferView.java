package com.personalfinance.transfer;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.personalfinance.account.AccountCurrency;

/** Detached transfer representation for templates. */
public record TransferView(
		Long id,
		Long sourceAccountId,
		String sourceAccountName,
		Long targetAccountId,
		String targetAccountName,
		BigDecimal amount,
		AccountCurrency currency,
		LocalDate transferDate,
		String description
) {
}
