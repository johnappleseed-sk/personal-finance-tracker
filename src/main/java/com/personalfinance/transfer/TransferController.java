package com.personalfinance.transfer;

import java.time.LocalDate;

import com.personalfinance.account.AccountService;
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

/** Coordinates account-to-account transfer pages. */
@Controller
@RequestMapping("/transfers")
public class TransferController {

	private final TransferService transferService;
	private final AccountService accountService;

	public TransferController(TransferService transferService, AccountService accountService) {
		this.transferService = transferService;
		this.accountService = accountService;
	}

	@InitBinder("transferForm")
	public void configureBinding(WebDataBinder binder) {
		binder.setAllowedFields("sourceAccountId", "targetAccountId", "amount", "transferDate", "description");
	}

	@GetMapping
	public String listTransfers(@AuthenticationPrincipal FinanceUserDetails user, Model model) {
		model.addAttribute("transfers", transferService.findTransfersForUser(user.getUserId()));
		model.addAttribute("accounts", accountService.findAccountsForUser(user.getUserId()));
		return "transfers/list";
	}

	@GetMapping("/new")
	public String showCreateForm(@AuthenticationPrincipal FinanceUserDetails user, Model model) {
		TransferForm form = new TransferForm();
		form.setTransferDate(LocalDate.now());
		model.addAttribute("transferForm", form);
		return prepareForm(user.getUserId(), model);
	}

	@PostMapping
	public String createTransfer(@AuthenticationPrincipal FinanceUserDetails user,
			@Valid @ModelAttribute("transferForm") TransferForm form, BindingResult errors,
			Model model, RedirectAttributes redirectAttributes) {
		if (!errors.hasErrors()) {
			try {
				transferService.createTransfer(user.getUserId(), form);
			} catch (TransferInputException exception) {
				errors.rejectValue(exception.getField(), "unavailable", exception.getMessage());
			}
		}
		if (errors.hasErrors()) {
			return prepareForm(user.getUserId(), model);
		}
		redirectAttributes.addFlashAttribute("successMessage", "Transfer recorded.");
		return "redirect:/transfers";
	}

	@GetMapping("/{transferId}/delete")
	public String showDeleteConfirmation(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long transferId, Model model) {
		model.addAttribute("transfer", transferService.findTransferForUser(user.getUserId(), transferId));
		return "transfers/delete";
	}

	@PostMapping("/{transferId}/delete")
	public String deleteTransfer(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long transferId, RedirectAttributes redirectAttributes) {
		transferService.deleteTransfer(user.getUserId(), transferId);
		redirectAttributes.addFlashAttribute("successMessage", "Transfer deleted.");
		return "redirect:/transfers";
	}

	private String prepareForm(Long userId, Model model) {
		model.addAttribute("pageTitle", "Transfer funds");
		model.addAttribute("accounts", accountService.findAccountsForUser(userId));
		model.addAttribute("today", LocalDate.now());
		return "transfers/form";
	}
}
