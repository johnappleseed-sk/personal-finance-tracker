package com.personalfinance.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import com.personalfinance.account.AccountCurrency;
import com.personalfinance.category.Category;
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

/** Monthly expense budget limit for a user-owned category and currency. */
@Entity
@Table(name = "budgets")
public class Budget {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 3)
	private AccountCurrency currency;

	@Column(name = "budget_month", nullable = false, length = 7)
	private String budgetMonth;

	@Column(name = "limit_amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal limitAmount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Budget() {
	}

	public Budget(Long userId, Category category, BudgetForm form) {
		this.userId = userId;
		updateDetails(category, form);
	}

	public void updateDetails(Category category, BudgetForm form) {
		this.category = category;
		this.currency = form.getCurrency();
		this.budgetMonth = form.getBudgetMonth();
		this.limitAmount = form.getLimitAmount().setScale(2, RoundingMode.UNNECESSARY);
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

	public Long getId() { return id; }
	public Long getUserId() { return userId; }
	public Category getCategory() { return category; }
	public AccountCurrency getCurrency() { return currency; }
	public String getBudgetMonth() { return budgetMonth; }
	public BigDecimal getLimitAmount() { return limitAmount; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
