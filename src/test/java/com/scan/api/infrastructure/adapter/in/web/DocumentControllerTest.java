package com.scan.api.infrastructure.adapter.in.web;

import com.scan.api.domain.model.Document;
import com.scan.api.domain.model.DocumentNotFoundException;
import com.scan.api.domain.port.in.DocumentUseCase;
import com.scan.api.infrastructure.security.JwtService;
import com.scan.api.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DocumentController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "app.auth.username=test-user",
        "app.auth.password-hash=$2b$10$kkze//w20clWp.X1RmThBu2mhsiOI8mdAAn1PKYxPpIqdO52vBjOu",
        "app.jwt.secret=test-secret-test-secret-test-secret-test-secret-0123456789"
})
class DocumentControllerTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @MockitoBean DocumentUseCase documentUseCase;

    private String bearer() {
        return "Bearer " + jwtService.generate("test-user", Map.of("roles", List.of("ROLE_ADMIN")));
    }

    private static Document doc(UUID id, Instant archivedAt) {
        return Document.builder().id(id).originalFilename("a.pdf").size(3).contentType("application/pdf")
                .sha256("secret-sha").s3Key("secret/key.pdf").createdAt(T0).archivedAt(archivedAt).build();
    }

    @Test
    void liste_sans_authentification_refusee() throws Exception {
        mvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());
    }

    @Test
    void liste_les_documents_actifs_sans_exposer_cle_ni_sha() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentUseCase.list(false)).thenReturn(List.of(doc(id, null)));

        mvc.perform(get("/api/documents").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].name").value("a.pdf"))
                .andExpect(jsonPath("$[0].size").value(3))
                .andExpect(jsonPath("$[0].archivedAt").doesNotExist())
                .andExpect(jsonPath("$[0].s3Key").doesNotExist())
                .andExpect(jsonPath("$[0].sha256").doesNotExist());
    }

    @Test
    void liste_les_documents_archives() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentUseCase.list(true)).thenReturn(List.of(doc(id, T0)));

        mvc.perform(get("/api/documents").param("archived", "true").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].archivedAt").isNotEmpty());
    }

    @Test
    void archive_et_restaure() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentUseCase.archive(id)).thenReturn(doc(id, T0));
        when(documentUseCase.unarchive(id)).thenReturn(doc(id, null));

        mvc.perform(post("/api/documents/{id}/archive", id).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());
        mvc.perform(post("/api/documents/{id}/unarchive", id).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").doesNotExist());
    }

    @Test
    void supprime_retourne_204() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(delete("/api/documents/{id}", id).header("Authorization", bearer()))
                .andExpect(status().isNoContent());
        verify(documentUseCase).delete(id);
    }

    @Test
    void document_inconnu_retourne_404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new DocumentNotFoundException(id)).when(documentUseCase).delete(id);

        mvc.perform(delete("/api/documents/{id}", id).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void suppression_sans_authentification_refusee() throws Exception {
        mvc.perform(delete("/api/documents/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}
