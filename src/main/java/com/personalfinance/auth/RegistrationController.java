package com.personalfinance.auth;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/** Renders registration forms and coordinates validation without exposing persistence entities. */
@Controller
public class RegistrationController {

	private final RegistrationService registrationService;

	public RegistrationController(RegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	/** Limits mass assignment to the four public registration fields. */
	@InitBinder("registrationForm")
	public void configureRegistrationBinding(WebDataBinder binder) {
		binder.setAllowedFields("name", "email", "password", "confirmPassword");
	}

	/** @return registration view with an empty form, not a database entity */
	@GetMapping("/register")
	public String showRegistration(Model model) {
		model.addAttribute("registrationForm", new RegistrationForm());
		return "auth/register";
	}

	/**
	 * Registers valid input or renders safe field errors. Password values are never echoed.
	 *
	 * @param form submitted public fields
	 * @param bindingResult MVC binding and validation errors
	 * @return form view on failure or a fixed success redirect (Post/Redirect/Get)
	 */
	@PostMapping("/register")
	public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
			BindingResult bindingResult) {
		if (!bindingResult.hasErrors()) {
			try {
				registrationService.register(form);
				return "redirect:/register/success";
			} catch (DuplicateEmailException exception) {
				bindingResult.rejectValue("email", "registration.emailUnavailable",
						"Registration could not be completed with this email address.");
			}
		}
		form.setPassword(null);
		form.setConfirmPassword(null);
		return "auth/register";
	}

	/** @return static confirmation without sensitive user details or automatic sign-in */
	@GetMapping("/register/success")
	public String showSuccess() {
		return "auth/registration-success";
	}
}
