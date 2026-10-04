package com.personalfinance.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/** Protects all non-public routes and retains CSRF protection during registration development. */
@Configuration
public class SecurityConfiguration {

	/**
	 * Allows registration and static CSS while requiring authentication elsewhere.
	 * Spring Security retains its session fixation protection, security headers, and POST logout.
	 *
	 * @param http Spring Security's filter-chain builder
	 * @return configured servlet security chain
	 * @throws Exception if security configuration cannot be built
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/register", "/register/success", "/css/**", "/error").permitAll()
						.anyRequest().authenticated())
				.formLogin(Customizer.withDefaults())
				.logout(Customizer.withDefaults())
				.build();
	}

	/** @return BCrypt encoder with work factor 12 for newly registered passwords */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	/**
	 * Prevents Spring Boot from creating a generated development user.
	 * Database-backed login is the next milestone; no identity can sign in yet.
	 *
	 * @return deny-all identity lookup until authentication is implemented
	 */
	@Bean
	public UserDetailsService pendingAuthenticationUserDetailsService() {
		return username -> {
			throw new UsernameNotFoundException("Sign-in is not available yet.");
		};
	}
}
