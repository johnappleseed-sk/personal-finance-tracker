package com.personalfinance.dashboard;

import java.math.BigDecimal;
import java.util.List;

import com.personalfinance.account.AccountCurrency;
import com.personalfinance.auth.AuthenticationController;
import com.personalfinance.security.FinanceUserDetails;
import com.personalfinance.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the real Thymeleaf view contract independently of database availability. */
@WebMvcTest(AuthenticationController.class)
@Import(SecurityConfiguration.class)
class DashboardRenderingTests {

	@Autowired private MockMvc mvc;
	@MockitoBean private DashboardService dashboardService;
	@MockitoBean private UserDetailsService userDetailsService;

	@Test
	void populatedDashboardRendersMonthlySavingsPropertiesFromCurrencySummary() throws Exception {
		var summary = new DashboardView.CurrencySummary(
				AccountCurrency.EUR, new BigDecimal("1020.00"), new BigDecimal("100.00"),
				new BigDecimal("80.00"), new BigDecimal("20.00"), 20, 100, 80, 1,
				new BigDecimal("1000.00"), new BigDecimal("80.00"), new BigDecimal("920.00"),
				92, new BigDecimal("13.33"), new BigDecimal("39.99"), "76.5 mo",
				List.of(), List.of(), List.of());
		when(dashboardService.buildDashboard(42L)).thenReturn(new DashboardView(
				"2026-11", List.of(summary), List.of(), List.of(), List.of(), List.of(),
				List.of(), 1, 0, 0, 0));
		var principal = new FinanceUserDetails(42L, "Alice", "alice@example.test", "unused-test-hash");

		String html = mvc.perform(get("/home").with(user(principal)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String goals = html.substring(html.indexOf("id=\"savings-goals-container\""));
		assertThat(goals).contains("EUR Monthly Savings Goal (20% Target)",
				"EUR 20.00", "20% Saved Rate", "width:20%", "Benchmark achieved (≥20% of income)")
				.doesNotContain("92% Saved Rate", "EUR 920.00");
		assertCustomGoalsUnavailable(html);
	}

	@ParameterizedTest
	@ValueSource(longs = {42L, 43L})
	void eachAuthenticatedUserSeesPreservationNoticeEvenWithoutAccounts(long userId) throws Exception {
		when(dashboardService.buildDashboard(userId)).thenReturn(new DashboardView(
				"2026-11", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0, 0, 0, 0));
		var principal = new FinanceUserDetails(userId, "User", "user@example.test", "unused-test-hash");

		String html = mvc.perform(get("/home").with(user(principal)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertCustomGoalsUnavailable(html);
		assertThat(html).contains("id=\"onboarding-title\"");
	}

	@Test
	void servedJavascriptDoesNotAccessOrRemoveLegacyFinancialGoals() throws Exception {
		String script = mvc.perform(get("/js/app.js"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		// Guard against restoring the legacy shared-storage path or adding silent cleanup.
		assertThat(script).doesNotContain("pf-custom-goals", "localStorage.clear(", "localStorage.removeItem(");
	}

	private void assertCustomGoalsUnavailable(String html) {
		assertThat(html).contains("id=\"custom-goals-notice\"", "Previously saved goals have not been deleted or migrated")
				.doesNotContain("id=\"custom-goal-form\"", "id=\"toggle-goal-form-btn\"", "data-remove-goal=");
	}
}
