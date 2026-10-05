package com.personalfinance.category;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Owner-scoped category queries; unscoped ID lookups must not be used to authorize resource access. */
public interface CategoryRepository extends JpaRepository<Category, Long> {

	/**
	 * Finds a category only when it belongs to the trusted user.
	 * @param id requested category
	 * @param userId authenticated database identity
	 * @return owned category, or empty for missing/foreign resources
	 */
	Optional<Category> findByIdAndUserId(Long id, Long userId);

	/**
	 * Lists one user's categories in PostgreSQL name order with ID as a tie-breaker.
	 * @param userId authenticated database identity
	 * @return only this user's categories
	 */
	List<Category> findAllByUserIdOrderByNameAscIdAsc(Long userId);
}
