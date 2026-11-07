package com.scan.api.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-test-secret-0123456789";

    private final JwtService jwt = new JwtService(SECRET, 30);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwt);

    @AfterEach
    void nettoyage() {
        SecurityContextHolder.clearContext();
    }

    private Authentication filtrer(String authorization) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (authorization != null) req.addHeader("Authorization", authorization);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).as("la chaîne doit toujours être appelée").isNotNull();
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void sans_en_tete_aucune_authentification() throws Exception {
        assertThat(filtrer(null)).isNull();
    }

    @Test
    void schema_autre_que_bearer_ignore() throws Exception {
        assertThat(filtrer("Basic dGVzdDp0ZXN0")).isNull();
    }

    @Test
    void jeton_valide_authentifie_avec_ses_roles() throws Exception {
        String token = jwt.generate("alice", Map.of("roles", List.of("ROLE_ADMIN")));

        Authentication auth = filtrer("Bearer " + token);

        assertThat(auth.getName()).isEqualTo("alice");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

    @Test
    void roles_non_listes_ne_donnent_aucun_droit() throws Exception {
        String token = jwt.generate("alice", Map.of("roles", "ROLE_ADMIN"));

        Authentication auth = filtrer("Bearer " + token);

        assertThat(auth.getName()).isEqualTo("alice");
        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    void jeton_invalide_efface_le_contexte() throws Exception {
        assertThat(filtrer("Bearer abc.def.ghi")).isNull();
    }

    @Test
    void bearer_vide_efface_le_contexte() throws Exception {
        assertThat(filtrer("Bearer ")).isNull();
    }
}
