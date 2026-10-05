package com.personalfinance.category;

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

/** Renders private category pages; the service enforces all ownership and persistence rules. */
@Controller
@RequestMapping("/categories")
public class CategoryController {

	private final CategoryService categoryService;

	public CategoryController(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

	/** Limits mass assignment to name and type; owner IDs come only from the principal. */
	@InitBinder("categoryForm")
	public void configureBinding(WebDataBinder binder) {
		binder.setAllowedFields("name", "categoryType");
	}

	/** @return only the authenticated user's categories */
	@GetMapping
	public String listCategories(@AuthenticationPrincipal FinanceUserDetails user, Model model) {
		model.addAttribute("categories", categoryService.findCategoriesForUser(user.getUserId()));
		return "categories/list";
	}

	/** @return an empty creation form with a display-only Expense default */
	@GetMapping("/new")
	public String showCreateForm(Model model) {
		CategoryForm form = new CategoryForm();
		form.setCategoryType(CategoryType.EXPENSE);
		model.addAttribute("categoryForm", form);
		return prepareForm(model, null);
	}

	/**
	 * Creates a category using Post/Redirect/Get; submitted owner IDs are ignored.
	 * @param user trusted principal
	 * @param form editable inputs
	 * @param errors validation/binding results
	 * @param model form view data on invalid input
	 * @param redirectAttributes fixed success notice
	 * @return form on validation error or fixed category-list redirect
	 */
	@PostMapping
	public String createCategory(@AuthenticationPrincipal FinanceUserDetails user,
			@Valid @ModelAttribute("categoryForm") CategoryForm form, BindingResult errors,
			Model model, RedirectAttributes redirectAttributes) {
		if (errors.hasErrors()) {
			return prepareForm(model, null);
		}
		categoryService.createCategory(user.getUserId(), form);
		redirectAttributes.addFlashAttribute("successMessage", "Category created.");
		return "redirect:/categories";
	}

	/** @return editable inputs only after the service authorizes this category */
	@GetMapping("/{categoryId}/edit")
	public String showEditForm(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long categoryId, Model model) {
		model.addAttribute("categoryForm", CategoryForm.from(categoryService.findCategoryForUser(user.getUserId(), categoryId)));
		return prepareForm(model, categoryId);
	}

	/**
	 * Verifies ownership even for invalid submissions before rendering an edit page.
	 * @param user trusted principal
	 * @param categoryId resource ID that does not itself authorize access
	 * @param form editable inputs
	 * @param errors validation/binding results
	 * @param model form view data
	 * @param redirectAttributes fixed success notice
	 * @return form on validation error or fixed category-list redirect
	 */
	@PostMapping("/{categoryId}")
	public String updateCategory(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long categoryId, @Valid @ModelAttribute("categoryForm") CategoryForm form,
			BindingResult errors, Model model, RedirectAttributes redirectAttributes) {
		categoryService.findCategoryForUser(user.getUserId(), categoryId);
		if (errors.hasErrors()) {
			return prepareForm(model, categoryId);
		}
		categoryService.updateCategory(user.getUserId(), categoryId, form);
		redirectAttributes.addFlashAttribute("successMessage", "Category updated.");
		return "redirect:/categories";
	}

	/** @return read-only delete confirmation; GET never changes persistent state */
	@GetMapping("/{categoryId}/delete")
	public String showDeleteConfirmation(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long categoryId, Model model) {
		model.addAttribute("category", categoryService.findCategoryForUser(user.getUserId(), categoryId));
		return "categories/delete";
	}

	/**
	 * Deletes an owned category after a CSRF-protected POST.
	 * @param user trusted principal
	 * @param categoryId resource to delete
	 * @param redirectAttributes fixed success notice
	 * @return fixed category-list redirect
	 */
	@PostMapping("/{categoryId}/delete")
	public String deleteCategory(@AuthenticationPrincipal FinanceUserDetails user,
			@PathVariable @Positive Long categoryId, RedirectAttributes redirectAttributes) {
		categoryService.deleteCategory(user.getUserId(), categoryId);
		redirectAttributes.addFlashAttribute("successMessage", "Category deleted.");
		return "redirect:/categories";
	}

	private String prepareForm(Model model, Long categoryId) {
		model.addAttribute("pageTitle", categoryId == null ? "Create category" : "Edit category");
		model.addAttribute("formAction", categoryId == null ? "/categories" : "/categories/" + categoryId);
		model.addAttribute("categoryTypes", CategoryType.values());
		return "categories/form";
	}
}
