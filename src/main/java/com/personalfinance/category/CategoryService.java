package com.personalfinance.category;

import java.util.List;

import com.personalfinance.user.User;
import com.personalfinance.user.UserNotFoundException;
import com.personalfinance.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Validates inputs and enforces ownership for category operations, including callers outside MVC. */
@Service
@Validated
@Transactional(readOnly = true)
public class CategoryService {

	private final CategoryRepository categoryRepository;
	private final UserRepository userRepository;

	public CategoryService(CategoryRepository categoryRepository, UserRepository userRepository) {
		this.categoryRepository = categoryRepository;
		this.userRepository = userRepository;
	}

	/**
	 * Lists only the session user's categories; entities are mapped within the transaction.
	 * @param userId trusted identity from the security principal
	 * @return detached views in name/ID order
	 */
	public List<CategoryView> findCategoriesForUser(@NotNull @Positive Long userId) {
		return categoryRepository.findAllByUserIdOrderByNameAscIdAsc(userId).stream().map(this::toView).toList();
	}

	/**
	 * Loads a category using both owner and resource ID.
	 * @param userId trusted authenticated identity
	 * @param categoryId requested resource
	 * @return authorized detached view
	 * @throws CategoryNotFoundException for either missing or foreign categories
	 */
	public CategoryView findCategoryForUser(@NotNull @Positive Long userId, @NotNull @Positive Long categoryId) {
		return toView(findOwnedCategory(userId, categoryId));
	}

	/**
	 * Creates a private category; duplicate names are allowed, and no default categories are seeded.
	 * @param userId trusted authenticated identity, never a submitted owner
	 * @param form validated editable fields
	 * @return saved detached view
	 * @throws UserNotFoundException if the session identity no longer exists
	 */
	@Transactional
	public CategoryView createCategory(@NotNull @Positive Long userId, @NotNull @Valid CategoryForm form) {
		User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
		Category category = new Category(user, form.getName(), form.getCategoryType());
		return toView(categoryRepository.saveAndFlush(category));
	}

	/**
	 * Changes an owned category's editable fields without changing ownership.
	 * @param userId trusted authenticated identity
	 * @param categoryId requested resource
	 * @param form validated inputs
	 * @throws CategoryNotFoundException for missing/foreign categories
	 */
	@Transactional
	public void updateCategory(@NotNull @Positive Long userId, @NotNull @Positive Long categoryId,
			@NotNull @Valid CategoryForm form) {
		Category category = findOwnedCategory(userId, categoryId);
		category.updateDetails(form.getName(), form.getCategoryType());
		categoryRepository.flush();
	}

	/**
	 * Deletes an owned category. Future transaction foreign keys must prevent history loss.
	 * @param userId trusted authenticated identity
	 * @param categoryId requested resource
	 * @throws CategoryNotFoundException for missing/foreign categories
	 */
	@Transactional
	public void deleteCategory(@NotNull @Positive Long userId, @NotNull @Positive Long categoryId) {
		categoryRepository.delete(findOwnedCategory(userId, categoryId));
	}

	private Category findOwnedCategory(Long userId, Long categoryId) {
		return categoryRepository.findByIdAndUserId(categoryId, userId).orElseThrow(CategoryNotFoundException::new);
	}

	private CategoryView toView(Category category) {
		return new CategoryView(category.getId(), category.getName(), category.getCategoryType());
	}
}
