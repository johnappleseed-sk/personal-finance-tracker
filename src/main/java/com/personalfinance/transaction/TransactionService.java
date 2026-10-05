package com.personalfinance.transaction;

import java.util.List;

import com.personalfinance.account.Account;
import com.personalfinance.account.AccountRepository;
import com.personalfinance.category.Category;
import com.personalfinance.category.CategoryRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Owns ledger rules: positive exact amounts, authorized references, matching category type, and atomic edits. */
@Service
@Validated
@Transactional(readOnly = true)
public class TransactionService {

	private final TransactionRepository transactionRepository;
	private final AccountRepository accountRepository;
	private final CategoryRepository categoryRepository;

	public TransactionService(TransactionRepository transactionRepository, AccountRepository accountRepository,
			CategoryRepository categoryRepository) {
		this.transactionRepository = transactionRepository;
		this.accountRepository = accountRepository;
		this.categoryRepository = categoryRepository;
	}

	/** @return only the trusted session user's entries, mapped inside a transaction */
	public List<TransactionView> findTransactionsForUser(@NotNull @Positive Long userId) {
		return transactionRepository.findAllByUserIdOrderByTransactionDateDescIdDesc(userId).stream().map(this::toView).toList();
	}

	/** @return authorized detached entry; missing/foreign IDs both raise a safe not-found error */
	public TransactionView findTransactionForUser(@NotNull @Positive Long userId, @NotNull @Positive Long transactionId) {
		return toView(transactionRepository.findByIdAndUserId(transactionId, userId)
				.orElseThrow(TransactionNotFoundException::new));
	}

	/**
	 * Creates a ledger entry using a trusted owner and references checked under row locks.
	 * @param userId authenticated owner, not form input
	 * @param form validated inputs; currency is always inferred from the account
	 * @return detached saved entry
	 */
	@Transactional
	public TransactionView createTransaction(@NotNull @Positive Long userId, @NotNull @Valid TransactionForm form) {
		Account account = findAccountForUpdate(userId, form.getAccountId());
		Category category = findCategoryForUpdate(userId, form);
		return toView(transactionRepository.saveAndFlush(new FinanceTransaction(userId, account, category, form)));
	}

	/**
	 * Atomically replaces an owned entry. Derived balances automatically reflect both old and new accounts.
	 * @param userId authenticated owner
	 * @param transactionId requested entry
	 * @param form validated replacement details
	 */
	@Transactional
	public void updateTransaction(@NotNull @Positive Long userId, @NotNull @Positive Long transactionId,
			@NotNull @Valid TransactionForm form) {
		FinanceTransaction transaction = findOwnedForUpdate(userId, transactionId);
		Account account = findAccountForUpdate(userId, form.getAccountId());
		Category category = findCategoryForUpdate(userId, form);
		transaction.updateDetails(account, category, form);
		transactionRepository.flush();
	}

	/** Deletes an owned entry; its balance effect disappears without adjusting any cached balance. */
	@Transactional
	public void deleteTransaction(@NotNull @Positive Long userId, @NotNull @Positive Long transactionId) {
		transactionRepository.delete(findOwnedForUpdate(userId, transactionId));
		transactionRepository.flush();
	}

	private FinanceTransaction findOwnedForUpdate(Long userId, Long id) {
		return transactionRepository.findOwnedForUpdate(id, userId).orElseThrow(TransactionNotFoundException::new);
	}

	private Account findAccountForUpdate(Long userId, Long accountId) {
		return accountRepository.findOwnedForUpdate(accountId, userId)
				.orElseThrow(() -> new TransactionInputException("accountId", "Choose an available account."));
	}

	private Category findCategoryForUpdate(Long userId, TransactionForm form) {
		Category category = categoryRepository.findOwnedForUpdate(form.getCategoryId(), userId)
				.orElseThrow(() -> new TransactionInputException("categoryId", "Choose an available category."));
		if (category.getCategoryType() != form.getTransactionType()) {
			throw new TransactionInputException("categoryId", "Choose a category matching the transaction type.");
		}
		return category;
	}

	private TransactionView toView(FinanceTransaction transaction) {
		return new TransactionView(transaction.getId(), transaction.getAccount().getId(), transaction.getAccount().getName(),
				transaction.getCategory().getId(), transaction.getCategory().getName(), transaction.getTransactionType(),
				transaction.getAmount(), transaction.getCurrency(), transaction.getTransactionDate(), transaction.getDescription());
	}
}
