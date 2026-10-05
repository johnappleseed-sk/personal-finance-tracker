package com.personalfinance.account;

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

	public AccountService(AccountRepository accountRepository, UserRepository userRepository) {
		this.accountRepository = accountRepository;
		this.userRepository = userRepository;
	}

	/**
	 * Lists only the authenticated user's accounts; amounts in different currencies are not summed.
	 *
	 * @param userId trusted identity from the security principal
	 * @return detached account views in name/ID order
	 */
	public List<AccountView> findAccountsForUser(@NotNull @Positive Long userId) {
		return accountRepository.findAllByUserIdOrderByNameAscIdAsc(userId).stream().map(this::toView).toList();
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
		return toView(findOwnedAccount(userId, accountId));
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
		return toView(accountRepository.saveAndFlush(account));
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
		Account account = findOwnedAccount(userId, accountId);
		account.updateDetails(form.getName(), form.getAccountType(), form.getInitialBalance(), form.getCurrency());
		accountRepository.flush();
	}

	/**
	 * Deletes only an ownership-checked account; future transaction foreign keys must prevent history loss.
	 *
	 * @param userId trusted authenticated identity
	 * @param accountId requested account
	 * @throws AccountNotFoundException if the account is missing or foreign
	 */
	@Transactional
	public void deleteAccount(@NotNull @Positive Long userId, @NotNull @Positive Long accountId) {
		accountRepository.delete(findOwnedAccount(userId, accountId));
	}

	private Account findOwnedAccount(Long userId, Long accountId) {
		return accountRepository.findByIdAndUserId(accountId, userId).orElseThrow(AccountNotFoundException::new);
	}

	private AccountView toView(Account account) {
		return new AccountView(account.getId(), account.getName(), account.getAccountType(), account.getInitialBalance(),
				account.getCurrency());
	}
}
