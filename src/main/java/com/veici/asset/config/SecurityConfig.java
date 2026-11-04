package com.veici.asset.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import com.veici.asset.security.AccountRefreshFilter;
import com.veici.asset.security.DatabaseUserDetailsService;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, DatabaseUserDetailsService userDetailsService)
            throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/favicon.ico").permitAll()
                        .requestMatchers("/assets", "/assets/**")
                        .hasRole("ASSET_ADMIN")
                        .requestMatchers("/employee/**")
                        .hasRole("EMPLOYEE")
                        .requestMatchers("/approver/**")
                        .hasRole("DEPT_APPROVER")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .addFilterBefore(
                new AccountRefreshFilter(userDetailsService),
                AuthorizationFilter.class
        );

        return http.build();
    }
}