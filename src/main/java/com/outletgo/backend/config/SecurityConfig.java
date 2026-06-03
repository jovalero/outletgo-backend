package com.outletgo.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
            // Disable CSRF for REST APIs
            .csrf(csrf -> csrf.disable())
            // Configure route authorization
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health").permitAll()         // Public health check
                .requestMatchers("/api/auth/**").permitAll()        // Public registration and login
                .requestMatchers("/api/products/**").permitAll()    // Public product catalog viewing
                .anyRequest().permitAll()                           // Permit all for easy collaborative frontend testing
            )
            // Disable default browser basic auth and login form since it's a stateless REST API
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable());
        
        return http.build();
    }
}
