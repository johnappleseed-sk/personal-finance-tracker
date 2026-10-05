package com.personalfinance.auth;

import com.personalfinance.security.FinanceUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Renders authentication and welcome pages; Spring Security handles POST login and logout. */
@Controller
public class AuthenticationController {

	/**
	 * Routes visitors to the appropriate fixed landing page without accepting return URLs.
	 *
	 * @param user session identity, or null for an anonymous visitor
	 * @return fixed login or authenticated home redirect
	 */
	@GetMapping("/")
	public String showRoot(@AuthenticationPrincipal FinanceUserDetails user) {
		return user == null ? "redirect:/login" : "redirect:/home";
	}

	/**
	 * Renders an empty login form; failed credentials are never placed in the model.
	 *
	 * @param user existing authenticated identity, if any
	 * @return login page or fixed home redirect for an already signed-in user
	 */
	@GetMapping("/login")
	public String showLogin(@AuthenticationPrincipal FinanceUserDetails user) {
		return user == null ? "auth/login" : "redirect:/home";
	}

	/**
	 * Displays only the authenticated user's name, never a browser-supplied user ID.
	 * This is an authentication landing page, not the future financial dashboard.
	 *
	 * @param user trusted identity supplied by Spring Security
	 * @param model view attributes, excluding entities and password hashes
	 * @return authenticated home view
	 */
	@GetMapping("/home")
	public String showHome(@AuthenticationPrincipal(errorOnInvalidType = true) FinanceUserDetails user, Model model) {
		model.addAttribute("displayName", user.getDisplayName());
		return "auth/home";
	}
}
