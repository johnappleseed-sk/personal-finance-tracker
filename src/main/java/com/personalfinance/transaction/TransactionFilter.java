package com.personalfinance.transaction;

import java.time.LocalDate;

import com.personalfinance.category.CategoryType;

/** Optional filter criteria for narrowing and searching a user's transaction ledger. */
public record TransactionFilter(
		Long accountId,
		Long categoryId,
		CategoryType type,
		LocalDate startDate,
		LocalDate endDate,
		String q
) {

	public TransactionFilter {
		q = (q == null || q.isBlank()) ? null : q.strip();
	}

	public static TransactionFilter empty() {
		return new TransactionFilter(null, null, null, null, null, null);
	}

	public boolean hasActiveFilters() {
		return accountId != null || categoryId != null || type != null
				|| startDate != null || endDate != null || q != null;
	}
}
