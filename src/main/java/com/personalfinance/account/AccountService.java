package com.personalfinance.account;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.personalfinance.transaction.HistoryConflictException;
import com.personalfinance.transaction.TransactionRepository;
import com.personalfinance.user.User;
import com.personalfinance.user.UserNotFoundException;
import com.personalfinance.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * Manages accounts using the authenticated user ID at every access boundary.
 * Maps entities to detached view data inside transactions; no owner is supplied by browser forms.
 */
@Service
@Validated
@Transactional(readOnly = true)
public class AccountService {

	private final AccountRepository accountRepository;
	private final UserRepository userRepository;
	private final TransactionRepository transactionRepository;

	public AccountService(AccountRepository accountRepository, UserRepository userRepository,
			TransactionRepository transactionRepository) {
		this.accountRepository = accountRepository;
		this.userRepository = userRepository;
		this.transactionRepository = transactionRepository;
	}

	/**
	 * Lists only the authenticated user's accounts; amounts in different currencies are not summed.
	 *
	 * @param userId trusted identity from the security principal
	 * @return detached account views in name/ID order
	 */
	public List<AccountView> findAccountsForUser(@NotNull @Positive Long userId) {
		Map<Long, BigDecimal> deltas = findDeltas(userId);
		return accountRepository.findAllByUserIdOrderByNameAscIdAsc(userId).stream()
				.map(account -> toView(account, deltas.getOrDefault(account.getId(), BigDecimal.ZERO))).toList();
	}

	/**
	 * Retrieves a user's own account for viewing or editing.
	 *
	 * @param userId trusted authenticated identity
	 * @param accountId requested account
	 * @return detached view of the authorized account
	 * @throws AccountNotFoundException if the account is absent or belongs to another user
	 */
	public AccountView findAccountForUser(@NotNull @Positive Long userId, @NotNull @Positive Long accountId) {
		return toView(findOwnedAccount(userId, accountId), findDeltas(userId).getOrDefault(accountId, BigDecimal.ZERO));
	}

	/**
	 * Creates an account without accepting an owner from form input.
	 *
	 * @param userId trusted authenticated identity
	 * @param form validated editable fields
	 * @return created account view
	 * @throws UserNotFoundException if the session user no longer exists
	 */
	@Transactional
	public AccountView createAccount(@NotNull @Positive Long userId, @NotNull @Valid AccountForm form) {
		User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
		Account account = new Account(user, form.getName(), form.getAccountType(), form.getInitialBalance(), form.getCurrency());
		return toView(accountRepository.saveAndFlush(account), BigDecimal.ZERO);
	}

	/**
	 * Updates an ownership-checked account; ownership and timestamps are not browser-editable.
	 *
	 * @param userId trusted authenticated identity
	 * @param accountId requested account
	 * @param form validated editable fields
	 * @throws AccountNotFoundException if the account is missing or foreign
	 */
	@Transactional
	public void updateAccount(@NotNull @Positive Long userId, @NotNull @Positive Long accountId,
			@NotNull @Valid AccountForm form) {
		Account account = accountRepository.findOwnedForUpdate(accountId, userId).orElseThrow(AccountNotFoundException::new);
		if (account.getCurrency() != form.getCurrency() && transactionRepository.existsByAccountIdAndUserId(accountId, userId)) {
			throw new HistoryConflictException("An account with transactions cannot change currency.");
		}
		account.updateDetails(form.getName(), form.getAccountType(), form.getInitialBalance(), form.getCurrency());
		accountRepository.flush();
	}

	/**
	 * Deletes only an ownership-checked account without transactions; never cascades ledger history.
	 *
	 * @param userId trusted authenticated identity
	 * @param accountId requested account
	 * @throws AccountNotFoundException if the account is missing or foreign
	 */
	@Transactional
	public void deleteAccount(@NotNull @Positive Long userId, @NotNull @Positive Long accountId) {
		Account account = accountRepository.findOwnedForUpdate(accountId, userId).orElseThrow(AccountNotFoundException::new);
		if (transactionRepository.existsByAccountIdAndUserId(accountId, userId)) {
			throw new HistoryConflictException("An account with transactions cannot be deleted. Keep it to preserve your history.");
		}
		accountRepository.delete(account);
		accountRepository.flush();
	}

	private Account findOwnedAccount(Long userId, Long accountId) {
		return accountRepository.findByIdAndUserId(accountId, userId).orElseThrow(AccountNotFoundException::new);
	}

	private Map<Long, BigDecimal> findDeltas(Long userId) {
		return transactionRepository.findAccountDeltas(userId).stream().collect(Collectors.toMap(
				TransactionRepository.AccountDelta::getAccountId, TransactionRepository.AccountDelta::getNetAmount));
	}

	private AccountView toView(Account account, BigDecimal delta) {
		return new AccountView(account.getId(), account.getName(), account.getAccountType(), account.getInitialBalance(),
				account.getCurrency(), account.getInitialBalance().add(delta));
	}
}
