package com.personalfinance.budget;

import java.time.YearMonth;
import java.util.List;

import com.personalfinance.account.AccountCurrency;
import com.personalfinance.category.CategoryService;
import com.personalfinance.category.CategoryType;
import com.personalfinance.category.CategoryView;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Coordinates user-owned monthly expense budget pages. */
@Controller
@RequestMapping("/budgets")
public class BudgetController {

	private final BudgetService budgetService;
	private final CategoryService categoryService;

	public BudgetController(BudgetService budgetService, CategoryService categoryService) {
		this.budgetService = budgetService;
		this.categoryService = categoryService;
	}

	@InitBinder("budgetForm")
	public void configureBinding(WebDataBinder binder) {
		binder.setAllowedFields("categoryId", "currency", "budgetMonth", "limitAmount");
	}

	@GetMapping
	public String listBudgets(@AuthenticationPrincipal FinanceUserDetails user,
			@RequestParam(name = "month", required = false) String month, Model model) {
		String activeMonth = (month != null && !month.isBlank()) ? month.strip() : null;
		List<BudgetView> budgets = budgetService.findBudgetsForUser(user.getUserId(), activeMonth);
		model.addAttribute("budgets", budgets);
		model.addAttribute("selectedMonth", activeMonth != null ? activeMonth : "");
		model.addAttribute("currentMonth", YearMonth.now().toString());
		model.addAttribute("overBudgetCount", budgets.stream().filter(BudgetView::overBudget).count());
		model.addAttribute("nearLimitCount", budgets.stream().filter(BudgetView::nearLimit).count());
		return "budgets/list";
	}

	@GetMapping("/new")
	public String showCreateForm(@AuthenticationPrincipal FinanceUserDetails user, Model model) {
		BudgetForm form = new BudgetForm();
		form.setCurrency(AccountCurrency.EUR);
		form.setBudgetMonth(YearMonth.now().toString());
		model.addAttribute("budgetForm", form);
		return prepareForm(user.getUserId(), model, null);
	}

	@PostMapping
	public String createBudget(@AuthenticationPrincipal FinanceUserDetails user,
			@Valid @ModelAttribute("budgetForm") BudgetForm form, BindingResult errors,
			Model model, RedirectAttributes redirectAttributes) {
		if (!errors.hasErrors()) {
			try {
				budgetService.createBudget(user.getUserId(), form);
			} catch (BudgetInputException exception) {
				errors.rejectValue(exception.getField(), "unavailable", exception.getMessage());
			}
		}
		if (errors.hasErrors()) {
			return prepareForm(user.getUserId(), model, null);
		}
		redirectAttributes.addFlashAttribute("successMessage", "Budget created.");
		return "redirect:/budgets";
	}

	@GetMapping("/{budgetId}/edit")
	public String showEditForm(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long budgetId, Model model) {
		model.addAttribute("budgetForm", BudgetForm.from(budgetService.findBudgetForUser(user.getUserId(), budgetId)));
		return prepareForm(user.getUserId(), model, budgetId);
	}

	@PostMapping("/{budgetId}")
	public String updateBudget(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long budgetId, @Valid @ModelAttribute("budgetForm") BudgetForm form,
			BindingResult errors, Model model, RedirectAttributes redirectAttributes) {
		budgetService.findBudgetForUser(user.getUserId(), budgetId);
		if (!errors.hasErrors()) {
			try {
				budgetService.updateBudget(user.getUserId(), budgetId, form);
			} catch (BudgetInputException exception) {
				errors.rejectValue(exception.getField(), "unavailable", exception.getMessage());
			}
		}
		if (errors.hasErrors()) {
			return prepareForm(user.getUserId(), model, budgetId);
		}
		redirectAttributes.addFlashAttribute("successMessage", "Budget updated.");
		return "redirect:/budgets";
	}

	@GetMapping("/{budgetId}/delete")
	public String showDeleteConfirmation(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long budgetId, Model model) {
		model.addAttribute("budget", budgetService.findBudgetForUser(user.getUserId(), budgetId));
		return "budgets/delete";
	}

	@PostMapping("/{budgetId}/delete")
	public String deleteBudget(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long budgetId, RedirectAttributes redirectAttributes) {
		budgetService.deleteBudget(user.getUserId(), budgetId);
		redirectAttributes.addFlashAttribute("successMessage", "Budget deleted.");
		return "redirect:/budgets";
	}

	private String prepareForm(Long userId, Model model, Long budgetId) {
		List<CategoryView> expenseCategories = categoryService.findCategoriesForUser(userId).stream()
				.filter(c -> c.categoryType() == CategoryType.EXPENSE)
				.toList();
		model.addAttribute("pageTitle", budgetId == null ? "Create budget" : "Edit budget");
		model.addAttribute("formAction", budgetId == null ? "/budgets" : "/budgets/" + budgetId);
		model.addAttribute("expenseCategories", expenseCategories);
		model.addAttribute("currencies", AccountCurrency.values());
		return "budgets/form";
	}
}
