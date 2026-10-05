package com.personalfinance.security;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * Session principal containing the authenticated database ID, not a JPA entity.
 * Future financial services must use this ID rather than IDs supplied by forms.
 * The superclass allows Spring Security to erase the password hash after authentication.
 */
public class FinanceUserDetails extends User {

	private static final long serialVersionUID = 1L;

	private final Long userId;
	private final String displayName;

	/**
	 * Copies only the identity fields required for authentication and a welcome message.
	 *
	 * @param userId persisted user's identifier
	 * @param displayName user's display name, escaped by the view
	 * @param email canonical email used as the login name
	 * @param passwordHash BCrypt hash, erased by Spring after successful authentication
	 */
	public FinanceUserDetails(Long userId, String displayName, String email, String passwordHash) {
		super(email, passwordHash, List.of(new SimpleGrantedAuthority("ROLE_USER")));
		this.userId = userId;
		this.displayName = displayName;
	}

	/** @return trusted identity to use for future user-owned resource queries */
	public Long getUserId() {
		return userId;
	}

	/** @return display name only, not a persistence entity or password hash */
	public String getDisplayName() {
		return displayName;
	}
}
