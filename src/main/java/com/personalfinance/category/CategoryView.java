package com.personalfinance.category;

/**
 * Detached category data with no owner entity or authentication details.
 * @param id resource ID, not authorization to access it
 * @param name display name escaped by templates
 * @param categoryType income/expense kind
 */
public record CategoryView(Long id, String name, CategoryType categoryType) {
}
