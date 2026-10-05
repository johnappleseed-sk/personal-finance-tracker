package com.personalfinance.category;

/** Stable persisted category kinds; transfers are not income or expense categories. */
public enum CategoryType {

	INCOME("Income"),
	EXPENSE("Expense");

	private final String label;

	CategoryType(String label) {
		this.label = label;
	}

	/** @return accessible display label for the category kind */
	public String getLabel() {
		return label;
	}
}
