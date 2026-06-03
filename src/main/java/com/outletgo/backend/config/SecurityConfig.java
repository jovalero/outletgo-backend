package com.outletgo.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Enable CORS and configure it
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Parse and set allowed origins
        if (allowedOrigins != null && !allowedOrigins.trim().isEmpty()) {
            List<String> origins = Arrays.asList(allowedOrigins.split(","));
            configuration.setAllowedOrigins(origins);
            // Credentials can only be allowed if allowed origins do not contain "*"
            if (origins.contains("*")) {
                configuration.setAllowCredentials(false);
            } else {
                configuration.setAllowCredentials(true);
            }
        } else {
            configuration.setAllowedOrigins(List.of("*"));
            configuration.setAllowCredentials(false);
        }
        
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Cache-Control", "Accept", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
