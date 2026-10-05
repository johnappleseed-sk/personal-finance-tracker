package com.personalfinance.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

import com.personalfinance.account.Account;
import com.personalfinance.account.AccountCurrency;
import com.personalfinance.category.Category;
import com.personalfinance.category.CategoryType;
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

/** Exact ledger entry. Ownership is immutable; currency is derived from the authorized account, never a form. */
@Entity
@Table(name = "transactions")
public class FinanceTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@Enumerated(EnumType.STRING)
	@Column(name = "transaction_type", nullable = false, length = 10)
	private CategoryType transactionType;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 3)
	private AccountCurrency currency;

	@Column(name = "transaction_date", nullable = false)
	private LocalDate transactionDate;

	@Column(nullable = false, length = 255)
	private String description;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Required by JPA; not used for browser binding. */
	protected FinanceTransaction() {
	}

	/**
	 * Creates a ledger entry after the service has validated input and authorized both references.
	 * @param userId trusted authenticated owner
	 * @param account authorized locked account
	 * @param category authorized locked category matching the transaction type
	 * @param form validated editable fields
	 */
	public FinanceTransaction(Long userId, Account account, Category category, TransactionForm form) {
		this.userId = userId;
		updateDetails(account, category, form);
	}

	/** Updates validated details without rounding money or changing the owner. */
	public void updateDetails(Account account, Category category, TransactionForm form) {
		this.account = account;
		this.category = category;
		this.transactionType = form.getTransactionType();
		this.amount = form.getAmount().setScale(2, RoundingMode.UNNECESSARY);
		this.currency = account.getCurrency();
		this.transactionDate = form.getTransactionDate();
		this.description = form.getDescription();
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
	public Account getAccount() { return account; }
	public Category getCategory() { return category; }
	public CategoryType getTransactionType() { return transactionType; }
	public BigDecimal getAmount() { return amount; }
	public AccountCurrency getCurrency() { return currency; }
	public LocalDate getTransactionDate() { return transactionDate; }
	public String getDescription() { return description; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
