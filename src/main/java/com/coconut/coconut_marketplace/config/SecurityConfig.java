package com.coconut.coconut_marketplace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/select-role", "/register/**", "/otp/**", "/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/buyer/**").hasRole("BUYER")
                .requestMatchers("/seller/**").hasRole("SELLER")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(customAuthenticationSuccessHandler())
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .sessionManagement(session -> session
                .invalidSessionUrl("/login?expired=true")
            );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler customAuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            var authorities = authentication.getAuthorities();
            boolean isBuyer = authorities.stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_BUYER"));
            boolean isSeller = authorities.stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_SELLER"));

            if (isBuyer) {
                response.sendRedirect("/buyer/dashboard");
            } else if (isSeller) {
                response.sendRedirect("/seller/dashboard");
            } else {
                response.sendRedirect("/login");
            }
        };
    }
}
