package com.personalfinance.transaction;

import java.time.LocalDate;

import com.personalfinance.account.AccountService;
import com.personalfinance.category.CategoryService;
import com.personalfinance.category.CategoryType;
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

/** Coordinates private ledger pages; the service checks every reference and ownership boundary. */
@Controller
@RequestMapping("/transactions")
public class TransactionController {

	private final TransactionService transactionService;
	private final AccountService accountService;
	private final CategoryService categoryService;

	public TransactionController(TransactionService transactionService, AccountService accountService,
			CategoryService categoryService) {
		this.transactionService = transactionService;
		this.accountService = accountService;
		this.categoryService = categoryService;
	}

	/** Owner, ID, inferred currency, and timestamps are not browser-editable. */
	@InitBinder("transactionForm")
	public void configureBinding(WebDataBinder binder) {
		binder.setAllowedFields("accountId", "categoryId", "transactionType", "amount", "transactionDate", "description");
	}

	/** @return newest-first entries owned by the authenticated user */
	@GetMapping
	public String listTransactions(@AuthenticationPrincipal FinanceUserDetails user, Model model) {
		model.addAttribute("transactions", transactionService.findTransactionsForUser(user.getUserId()));
		return "transactions/list";
	}

	/** @return form with display-only defaults and this user's account/category choices */
	@GetMapping("/new")
	public String showCreateForm(@AuthenticationPrincipal FinanceUserDetails user, Model model) {
		TransactionForm form = new TransactionForm();
		form.setTransactionType(CategoryType.EXPENSE);
		form.setTransactionDate(LocalDate.now());
		model.addAttribute("transactionForm", form);
		return prepareForm(user.getUserId(), model, null);
	}

	/** Validates inputs, translates safe reference errors, and redirects after successful creation. */
	@PostMapping
	public String createTransaction(@AuthenticationPrincipal FinanceUserDetails user,
			@Valid @ModelAttribute("transactionForm") TransactionForm form, BindingResult errors,
			Model model, RedirectAttributes redirectAttributes) {
		if (!errors.hasErrors()) {
			try {
				transactionService.createTransaction(user.getUserId(), form);
			} catch (TransactionInputException exception) {
				errors.rejectValue(exception.getField(), "unavailable", exception.getMessage());
			}
		}
		if (errors.hasErrors()) {
			return prepareForm(user.getUserId(), model, null);
		}
		redirectAttributes.addFlashAttribute("successMessage", "Transaction created.");
		return "redirect:/transactions";
	}

	/** @return editable fields only after an owner-scoped transaction lookup */
	@GetMapping("/{transactionId}/edit")
	public String showEditForm(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long transactionId, Model model) {
		model.addAttribute("transactionForm", TransactionForm.from(transactionService.findTransactionForUser(user.getUserId(), transactionId)));
		return prepareForm(user.getUserId(), model, transactionId);
	}

	/** Checks transaction ownership even on invalid submissions; authorized reference errors remain field errors. */
	@PostMapping("/{transactionId}")
	public String updateTransaction(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long transactionId, @Valid @ModelAttribute("transactionForm") TransactionForm form,
			BindingResult errors, Model model, RedirectAttributes redirectAttributes) {
		transactionService.findTransactionForUser(user.getUserId(), transactionId);
		if (!errors.hasErrors()) {
			try {
				transactionService.updateTransaction(user.getUserId(), transactionId, form);
			} catch (TransactionInputException exception) {
				errors.rejectValue(exception.getField(), "unavailable", exception.getMessage());
			}
		}
		if (errors.hasErrors()) {
			return prepareForm(user.getUserId(), model, transactionId);
		}
		redirectAttributes.addFlashAttribute("successMessage", "Transaction updated.");
		return "redirect:/transactions";
	}

	/** @return read-only confirmation; GET does not remove an entry or affect balances */
	@GetMapping("/{transactionId}/delete")
	public String showDeleteConfirmation(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long transactionId, Model model) {
		model.addAttribute("transaction", transactionService.findTransactionForUser(user.getUserId(), transactionId));
		return "transactions/delete";
	}

	/** Deletes only an owned entry through a CSRF-protected POST; balances are derived on the next read. */
	@PostMapping("/{transactionId}/delete")
	public String deleteTransaction(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long transactionId, RedirectAttributes redirectAttributes) {
		transactionService.deleteTransaction(user.getUserId(), transactionId);
		redirectAttributes.addFlashAttribute("successMessage", "Transaction deleted.");
		return "redirect:/transactions";
	}

	private String prepareForm(Long userId, Model model, Long transactionId) {
		model.addAttribute("pageTitle", transactionId == null ? "Create transaction" : "Edit transaction");
		model.addAttribute("formAction", transactionId == null ? "/transactions" : "/transactions/" + transactionId);
		model.addAttribute("accounts", accountService.findAccountsForUser(userId));
		model.addAttribute("categories", categoryService.findCategoriesForUser(userId));
		model.addAttribute("transactionTypes", CategoryType.values());
		model.addAttribute("today", LocalDate.now());
		return "transactions/form";
	}
}
