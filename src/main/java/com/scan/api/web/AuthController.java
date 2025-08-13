package com.scan.api.web;

import com.scan.api.security.JwtService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        User principal = (User) auth.getPrincipal();
        List<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority()).toList();

        String token = jwtService.generate(principal.getUsername(), Map.of("roles", roles));

        return ResponseEntity.ok(Map.of("token", token));
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
