package com.personalfinance.category;

/** Indistinguishable missing/foreign resource error that does not disclose another user's categories. */
public class CategoryNotFoundException extends RuntimeException {

	/** Creates a non-sensitive error without IDs or category names. */
	public CategoryNotFoundException() {
		super("Category not found.");
	}
}
