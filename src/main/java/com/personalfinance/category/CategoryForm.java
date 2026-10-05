package com.personalfinance.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Editable category inputs only; the browser cannot assign ownership, IDs, or timestamps. */
public class CategoryForm {

	@NotBlank(message = "Enter a category name.")
	@Size(max = 100, message = "Use at most 100 characters for the category name.")
	private String name;

	@NotNull(message = "Choose a category type.")
	private CategoryType categoryType;

	/**
	 * Copies an ownership-checked view into form inputs.
	 * @param category authorized, detached view
	 * @return editable fields only
	 */
	public static CategoryForm from(CategoryView category) {
		CategoryForm form = new CategoryForm();
		form.setName(category.name());
		form.setCategoryType(category.categoryType());
		return form;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name == null ? null : name.strip();
	}

	public CategoryType getCategoryType() {
		return categoryType;
	}

	public void setCategoryType(CategoryType categoryType) {
		this.categoryType = categoryType;
	}
}
