package com.personalfinance.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;

/** Configures session-based authentication, BCrypt verification, and CSRF-protected login/logout. */
@Configuration
public class SecurityConfiguration {

	/**
	 * Allows public authentication pages while requiring authentication elsewhere.
	 * Fixed redirects and a disabled saved-request cache avoid untrusted return URLs.
	 * Spring Security retains session fixation protection and its default security headers.
	 *
	 * @param http Spring Security's filter-chain builder
	 * @return configured servlet security chain
	 * @throws Exception if security configuration cannot be built
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/", "/login", "/register", "/register/success", "/css/**", "/error").permitAll()
						.anyRequest().authenticated())
				.requestCache(cache -> cache.requestCache(new NullRequestCache()))
				.formLogin(form -> form
						.loginPage("/login")
						.usernameParameter("email")
						.defaultSuccessUrl("/home", true)
						.failureUrl("/login?error"))
				.logout(logout -> logout
						.logoutUrl("/logout")
						.logoutSuccessUrl("/login?logout")
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies("JSESSIONID"))
				.build();
	}

	/** @return BCrypt encoder with work factor 12 and strict byte-limit checks for registration and login */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new StrictBCryptPasswordEncoder();
	}
}
