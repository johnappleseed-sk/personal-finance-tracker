package com.personalfinance.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.personalfinance.account.AccountCurrency;
import com.personalfinance.category.CategoryType;

/** Detached, owner-authorized ledger data; amounts are positive and their sign is determined by type. */
public record TransactionView(Long id, Long accountId, String accountName, Long categoryId, String categoryName,
		CategoryType transactionType, BigDecimal amount, AccountCurrency currency, LocalDate transactionDate,
		String description) {
}
