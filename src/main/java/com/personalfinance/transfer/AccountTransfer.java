package com.personalfinance.transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

import com.personalfinance.account.Account;
import com.personalfinance.account.AccountCurrency;
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

/** Exact same-currency transfer between two accounts owned by the same user. */
@Entity
@Table(name = "transfers")
public class AccountTransfer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "source_account_id", nullable = false)
	private Account sourceAccount;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "target_account_id", nullable = false)
	private Account targetAccount;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 3)
	private AccountCurrency currency;

	@Column(name = "transfer_date", nullable = false)
	private LocalDate transferDate;

	@Column(nullable = false, length = 255)
	private String description;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected AccountTransfer() {
	}

	public AccountTransfer(Long userId, Account sourceAccount, Account targetAccount, TransferForm form) {
		this.userId = userId;
		this.sourceAccount = sourceAccount;
		this.targetAccount = targetAccount;
		this.amount = form.getAmount().setScale(2, RoundingMode.UNNECESSARY);
		this.currency = sourceAccount.getCurrency();
		this.transferDate = form.getTransferDate();
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
	public Long getUserId() { return userId; }
	public Account getSourceAccount() { return sourceAccount; }
	public Account getTargetAccount() { return targetAccount; }
	public BigDecimal getAmount() { return amount; }
	public AccountCurrency getCurrency() { return currency; }
	public LocalDate getTransferDate() { return transferDate; }
	public String getDescription() { return description; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
