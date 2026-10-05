package com.personalfinance.account;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

/**
 * Persists a user-owned financial account and its opening balance, not a calculated current balance.
 * Ownership is assigned on creation and cannot be changed through account updates.
 */
@Entity
@Table(name = "accounts")
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	private User user;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "account_type", nullable = false, length = 20)
	private AccountType accountType;

	@Column(name = "initial_balance", nullable = false, precision = 19, scale = 2)
	private BigDecimal initialBalance;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 3)
	private AccountCurrency currency;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Required by JPA; not a browser-binding constructor. */
	protected Account() {
	}

	/**
	 * Creates an account with a trusted owner and validated monetary values.
	 *
	 * @param user owner resolved from the authenticated identity
	 * @param name validated display name
	 * @param accountType supported account kind
	 * @param initialBalance opening balance with at most two decimal places
	 * @param currency supported two-decimal currency
	 */
	public Account(User user, String name, AccountType accountType, BigDecimal initialBalance, AccountCurrency currency) {
		this.user = user;
		updateDetails(name, accountType, initialBalance, currency);
	}

	/**
	 * Changes editable details without changing ownership or silently rounding money.
	 *
	 * @param name validated display name
	 * @param accountType supported account kind
	 * @param initialBalance validated opening balance; extra nonzero decimals are rejected
	 * @param currency supported two-decimal currency
	 */
	public void updateDetails(String name, AccountType accountType, BigDecimal initialBalance, AccountCurrency currency) {
		this.name = name;
		this.accountType = accountType;
		this.initialBalance = initialBalance.setScale(2, RoundingMode.UNNECESSARY);
		this.currency = currency;
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
	public String getName() { return name; }
	public AccountType getAccountType() { return accountType; }
	public BigDecimal getInitialBalance() { return initialBalance; }
	public AccountCurrency getCurrency() { return currency; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
