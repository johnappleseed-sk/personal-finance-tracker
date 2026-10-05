package com.personalfinance.account;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Public account inputs only; IDs, ownership, timestamps, and calculated balances are not bindable. */
public class AccountForm {

	@NotBlank(message = "Enter an account name.")
	@Size(max = 100, message = "Use at most 100 characters for the account name.")
	private String name;

	@NotNull(message = "Choose an account type.")
	private AccountType accountType;

	@NotNull(message = "Enter an opening balance.")
	@Digits(integer = 17, fraction = 2, message = "Use at most 17 whole-number digits and 2 decimal places.")
	private BigDecimal initialBalance;

	@NotNull(message = "Choose a currency.")
	private AccountCurrency currency;

	/**
	 * Copies an ownership-checked view into editable input fields.
	 *
	 * @param account account already authorized by the service
	 * @return form containing only editable fields
	 */
	public static AccountForm from(AccountView account) {
		AccountForm form = new AccountForm();
		form.setName(account.name());
		form.setAccountType(account.accountType());
		form.setInitialBalance(account.initialBalance());
		form.setCurrency(account.currency());
		return form;
	}

	public String getName() { return name; }
	public void setName(String name) { this.name = name == null ? null : name.strip(); }
	public AccountType getAccountType() { return accountType; }
	public void setAccountType(AccountType accountType) { this.accountType = accountType; }
	public BigDecimal getInitialBalance() { return initialBalance; }
	public void setInitialBalance(BigDecimal initialBalance) { this.initialBalance = initialBalance; }
	public AccountCurrency getCurrency() { return currency; }
	public void setCurrency(AccountCurrency currency) { this.currency = currency; }
}
