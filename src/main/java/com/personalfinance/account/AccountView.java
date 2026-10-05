package com.personalfinance.account;

import java.math.BigDecimal;

/**
 * Immutable view data detached from JPA; no owner entity or authentication secrets are exposed.
 *
 * @param id account identifier, not sufficient by itself to authorize access
 * @param name display name
 * @param accountType account kind
 * @param initialBalance opening balance, not a transaction-derived current balance
 * @param currency currency code associated with the amount
 */
public record AccountView(Long id, String name, AccountType accountType, BigDecimal initialBalance,
		AccountCurrency currency) {
}
