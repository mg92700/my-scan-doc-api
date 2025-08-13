package com.scan.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                Jws<Claims> jws = jwtService.parse(token);
                String username = jws.getBody().getSubject();

                // Rôles éventuels dans claim "roles": ["ROLE_ADMIN", ...]
                Object rolesObj = jws.getBody().get("roles");
                List<GrantedAuthority> authorities = new ArrayList<>();
                if (rolesObj instanceof Collection<?> roles) {
                    for (Object r : roles) {
                        authorities.add(new SimpleGrantedAuthority(String.valueOf(r)));
                    }
                }

                Authentication authToken =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (JwtException ex) {
                // invalide => on laisse non authentifié; Spring Security rejettera si protégé
            }
        }
        chain.doFilter(request, response);
    }
}