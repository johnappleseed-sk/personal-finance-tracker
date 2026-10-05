package com.personalfinance.account;

import java.math.BigDecimal;

import com.personalfinance.security.FinanceUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Coordinates mobile account pages; all ownership and persistence rules are enforced by the service. */
@Controller
@RequestMapping("/accounts")
public class AccountController {

	private final AccountService accountService;

	public AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	/** Limits mass assignment to editable fields; ownership comes only from the principal. */
	@InitBinder("accountForm")
	public void configureBinding(WebDataBinder binder) {
		binder.setAllowedFields("name", "accountType", "initialBalance", "currency");
	}

	/** @return list containing only the signed-in user's accounts */
	@GetMapping
	public String listAccounts(@AuthenticationPrincipal FinanceUserDetails user, Model model) {
		model.addAttribute("accounts", accountService.findAccountsForUser(user.getUserId()));
		return "accounts/list";
	}

	/** @return creation form with explicit display defaults (not defaults for submitted input) */
	@GetMapping("/new")
	public String showCreateForm(Model model) {
		AccountForm form = new AccountForm();
		form.setAccountType(AccountType.CHECKING);
		form.setCurrency(AccountCurrency.EUR);
		form.setInitialBalance(BigDecimal.ZERO);
		model.addAttribute("accountForm", form);
		return prepareForm(model, null);
	}

	/**
	 * Creates a validated account for the session user using Post/Redirect/Get.
	 *
	 * @param user trusted security principal
	 * @param form submitted editable fields
	 * @param errors binding/validation results
	 * @param model form view data on validation failure
	 * @param redirectAttributes fixed, non-sensitive success notice
	 * @return form on invalid input or fixed accounts redirect
	 */
	@PostMapping
	public String createAccount(@AuthenticationPrincipal FinanceUserDetails user,
			@Valid @ModelAttribute("accountForm") AccountForm form, BindingResult errors,
			Model model, RedirectAttributes redirectAttributes) {
		if (errors.hasErrors()) {
			return prepareForm(model, null);
		}
		accountService.createAccount(user.getUserId(), form);
		redirectAttributes.addFlashAttribute("successMessage", "Account created.");
		return "redirect:/accounts";
	}

	/** @return editable fields only after confirming that the account belongs to the session user */
	@GetMapping("/{accountId}/edit")
	public String showEditForm(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long accountId, Model model) {
		model.addAttribute("accountForm", AccountForm.from(accountService.findAccountForUser(user.getUserId(), accountId)));
		return prepareForm(model, accountId);
	}

	/**
	 * Checks ownership even for invalid form submissions before rendering any account page.
	 *
	 * @param user trusted security principal
	 * @param accountId requested account, never sufficient alone for access
	 * @param form submitted editable fields
	 * @param errors binding/validation results
	 * @param model form view data
	 * @param redirectAttributes fixed success notice
	 * @return form on invalid input or fixed accounts redirect
	 */
	@PostMapping("/{accountId}")
	public String updateAccount(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long accountId, @Valid @ModelAttribute("accountForm") AccountForm form,
			BindingResult errors, Model model, RedirectAttributes redirectAttributes) {
		accountService.findAccountForUser(user.getUserId(), accountId);
		if (errors.hasErrors()) {
			return prepareForm(model, accountId);
		}
		accountService.updateAccount(user.getUserId(), accountId, form);
		redirectAttributes.addFlashAttribute("successMessage", "Account updated.");
		return "redirect:/accounts";
	}

	/** @return a read-only confirmation page; GET never deletes an account */
	@GetMapping("/{accountId}/delete")
	public String showDeleteConfirmation(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long accountId, Model model) {
		model.addAttribute("account", accountService.findAccountForUser(user.getUserId(), accountId));
		return "accounts/delete";
	}

	/**
	 * Deletes only the session user's account after a CSRF-protected POST.
	 *
	 * @param user trusted security principal
	 * @param accountId account to delete
	 * @param redirectAttributes fixed success notice
	 * @return fixed accounts redirect
	 */
	@PostMapping("/{accountId}/delete")
	public String deleteAccount(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long accountId, RedirectAttributes redirectAttributes) {
		accountService.deleteAccount(user.getUserId(), accountId);
		redirectAttributes.addFlashAttribute("successMessage", "Account deleted.");
		return "redirect:/accounts";
	}

	private String prepareForm(Model model, Long accountId) {
		model.addAttribute("pageTitle", accountId == null ? "Create account" : "Edit account");
		model.addAttribute("formAction", accountId == null ? "/accounts" : "/accounts/" + accountId);
		model.addAttribute("accountTypes", AccountType.values());
		model.addAttribute("currencies", AccountCurrency.values());
		return "accounts/form";
	}
}
