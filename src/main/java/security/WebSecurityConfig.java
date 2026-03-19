package com.leviathanledger.leviathan.security;

import com.leviathanledger.leviathan.security.jwt.AuthEntryPointJwt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

/**
 * LexTracker Security Engine
 * Purpose: Enforces the "Digital Shield" by managing JWT validation,
 * CORS policy, and Persona-based access control.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // CRITICAL: This enables @PreAuthorize for Lawyer/Clerk roles
public class WebSecurityConfig {

    @Autowired
    private AuthEntryPointJwt unauthorizedHandler;

    @Autowired
    private com.leviathanledger.leviathan.security.AuthTokenFilter authTokenFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * CORS Configuration: Strictly allows the LexTracker Next.js Frontend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Explicitly allow the local development origin
        configuration.setAllowedOrigins(Collections.singletonList("http://localhost:3000"));

        // Methods required for Case Management & Evidence Hashing
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        // Standard headers + Authorization for our "Chain of Custody" tokens
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));

        // Allow the browser to expose the Authorization header to our Next.js App
        configuration.setExposedHeaders(Collections.singletonList("Authorization"));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // Cache pre-flight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable()) // Stateless JWT doesn't require CSRF
                .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth ->
                        auth
                                // Public entry points: The "Vault Door" must be open to let people sign in
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                .requestMatchers("/api/auth/**").permitAll()
                                .requestMatchers("/auth/**").permitAll()
                                .requestMatchers("/h2-console/**").permitAll()

                                // Protected evidence: Only authenticated Personas can view files
                                .requestMatchers("/uploads/**").authenticated()

                                // Case Management endpoints
                                .requestMatchers("/api/cases/**").authenticated()
                                .requestMatchers("/api/documents/**").authenticated()

                                // All other endpoints require a valid JWT
                                .anyRequest().authenticated()
                );

        // Security Header Fix for H2 Console & Secure Frames
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        // Add the JWT Filter (The "Digital Handshake" verifier)
        http.addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}