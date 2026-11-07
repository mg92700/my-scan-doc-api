package com.scan.api.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Configuration Spring Security. Le compte de service est défini par app.auth.username / app.auth.password-hash (.env).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${app.auth.username}") String username,
            @Value("${app.auth.password-hash}") String passwordHash) {
        // Le mot de passe n'est jamais stocké en clair : seul son hash BCrypt est fourni (fichier .env).
        if (!passwordHash.startsWith("$2")) {
            throw new IllegalStateException("app.auth.password-hash doit être un hash BCrypt ($2a/$2b/$2y)");
        }
        UserDetails user = User.withUsername(username)
                .password(passwordHash)
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService uds,
                                                       PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(uds);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Chaîne de filtres HTTP (voir SECURITY.md).
     * - API sans session ni cookie : CSRF désactivé volontairement, il n'y a rien à détourner.
     * - Authentification par JWT (Bearer) uniquement : HTTP Basic et formulaire désactivés.
     * - Accès fermé par défaut : seules les routes listées sont ouvertes, tout le reste est refusé.
     * - Non authentifié → 401, authentifié mais interdit → 403.
     * - En-têtes : CSP restrictive (l'API ne sert aucune page) et pas de Referer.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        JwtAuthFilter jwtFilter = new JwtAuthFilter(jwtService);

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll()
                );

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
