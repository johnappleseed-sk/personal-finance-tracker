package com.personalfinance.category;

import java.time.Instant;

import com.personalfinance.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Persists a private income/expense label; not bound directly to browser input or rendered as view data. */
@Entity
@Table(name = "categories")
public class Category {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	private User user;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "category_type", nullable = false, length = 10)
	private CategoryType categoryType;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Required by JPA, not for browser binding. */
	protected Category() {
	}

	/**
	 * Creates a category with an immutable owner.
	 * @param user owner resolved from a trusted session identity
	 * @param name validated, trimmed display name
	 * @param categoryType supported income/expense kind
	 */
	public Category(User user, String name, CategoryType categoryType) {
		this.user = user;
		updateDetails(name, categoryType);
	}

	/**
	 * Updates editable fields without reassigning ownership.
	 * @param name validated, trimmed display name
	 * @param categoryType supported kind; future transactions must protect historical semantics
	 */
	public void updateDetails(String name, CategoryType categoryType) {
		this.name = name;
		this.categoryType = categoryType;
	}

	@PrePersist
	private void initializeTimestamps() {
		createdAt = Instant.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	private void updateTimestamp() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public CategoryType getCategoryType() {
		return categoryType;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
