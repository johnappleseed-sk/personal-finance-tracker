package com.personalfinance.ui;

import java.util.List;

import com.personalfinance.auth.AuthenticationController;
import com.personalfinance.dashboard.DashboardService;
import com.personalfinance.dashboard.DashboardView;
import com.personalfinance.security.FinanceUserDetails;
import com.personalfinance.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checks shared view/security contracts without modifying the user's database or using mock UI data in production. */
@WebMvcTest(AuthenticationController.class)
@Import(SecurityConfiguration.class)
class UiRenderingTests {

	@Autowired private MockMvc mvc;
	@MockitoBean private DashboardService dashboardService;
	@MockitoBean private UserDetailsService userDetailsService;

	@Test
	void anonymousLoginLoadsLocalIconsAndKeepsAccessibleTextAndCsrf() throws Exception {
		String html = mvc.perform(get("/login")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(html).contains("/vendor/lucide/lucide.min.js", "/css/product.css", "data-lucide=\"eye\"",
				"/css/login.css", "class=\"login-layout\"", "class=\"login-introduction\"",
				"name=\"_csrf\"", "autocomplete=\"current-password\"", "aria-pressed=\"false\"")
				.doesNotContain("cdn.jsdelivr.net", "256-Bit BCrypt");
		mvc.perform(get("/vendor/lucide/lucide.min.js")).andExpect(status().isOk());
		mvc.perform(get("/css/product.css")).andExpect(status().isOk());
		mvc.perform(get("/css/login.css")).andExpect(status().isOk());
		mvc.perform(get("/accounts")).andExpect(status().is3xxRedirection());
	}

	@Test
	void dashboardSharesAccessibleNavigationAndPreservesRealRoutes() throws Exception {
		when(dashboardService.buildDashboard(42L)).thenReturn(new DashboardView("2026-11", List.of(),
				List.of(), List.of(), List.of(), List.of(), List.of(), 0, 0, 0, 0));
		var principal = new FinanceUserDetails(42L, "<script>User</script>", "ui@example.test", "unused-test-hash");
		String html = mvc.perform(get("/home").with(user(principal))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(html).contains("class=\"skip-link\"", "aria-controls=\"primary-nav-links\"",
				"aria-current=\"page\"", "data-lucide=\"wallet\"", "&lt;script&gt;User&lt;/script&gt;",
				"href=\"/transactions\"", "href=\"/reports\"", "href=\"/budgets\"", "href=\"/transfers\"",
				"id=\"onboarding-title\"", "id=\"custom-goals-notice\"")
				.doesNotContain("<script>User</script>");
	}

	@Test
	void allApplicationTemplatesUseSvgIconsInsteadOfEmoji() throws Exception {
		var resolver = new PathMatchingResourcePatternResolver();
		for (var resource : resolver.getResources("classpath*:templates/**/*.html")) {
			try (var input = resource.getInputStream()) {
				String html = new String(input.readAllBytes(), UTF_8);
				assertThat(html).as(resource.getFilename()).doesNotContainPattern("[\\x{1F1E6}-\\x{1FAFF}\\x{2600}-\\x{27BF}]");
			}
		}
	}
}
