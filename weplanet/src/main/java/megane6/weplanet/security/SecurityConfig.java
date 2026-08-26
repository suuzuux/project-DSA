package megane6.weplanet.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private final LoginSuccessHandler loginSuccessHandler;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(auth -> auth
						.anyRequest().permitAll()
				)
				.formLogin(form -> form
						.loginPage("/login")
						.loginProcessingUrl("/login")
						.successHandler(loginSuccessHandler)
						.failureHandler(portalAwareFailureHandler())
						.permitAll()
				)
				.logout(logout -> logout
						.logoutRequestMatcher(request -> "/logout".equals(request.getServletPath()))
						.logoutSuccessUrl("/")
						.permitAll()
				)
				.csrf(csrf -> csrf.disable());

		return http.build();
	}

	private AuthenticationFailureHandler portalAwareFailureHandler() {
		return (request, response, exception) -> {
			if ("true".equals(request.getParameter("portalLogin"))) {
				response.sendRedirect("/portal/login?error");
				return;
			}
			response.sendRedirect("/login?error");
		};
	}
}
