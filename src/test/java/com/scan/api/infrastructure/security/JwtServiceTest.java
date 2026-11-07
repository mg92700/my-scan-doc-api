package com.scan.api.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-test-secret-0123456789";

    @Test
    void jeton_genere_est_relu_a_l_identique() {
        JwtService jwt = new JwtService(SECRET, 30);

        String token = jwt.generate("alice", Map.of("roles", List.of("ROLE_ADMIN")));
        Claims claims = jwt.parse(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("roles")).isEqualTo(List.of("ROLE_ADMIN"));
    }

    @Test
    void jeton_signe_avec_un_autre_secret_est_refuse() {
        String token = new JwtService(SECRET, 30).generate("alice", Map.of());
        JwtService autre = new JwtService("autre-secret-autre-secret-autre-secret-autre-secret-99", 30);

        assertThatThrownBy(() -> autre.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void jeton_expire_est_refuse() {
        JwtService expire = new JwtService(SECRET, -1);
        String token = expire.generate("alice", Map.of());

        assertThatThrownBy(() -> expire.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void secret_trop_court_empeche_le_demarrage() {
        assertThatThrownBy(() -> new JwtService("court", 30)).isInstanceOf(WeakKeyException.class);
    }
}
