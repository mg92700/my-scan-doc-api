package com.scan.api.infrastructure.adapter.in.web;

import com.scan.api.domain.model.Document;
import com.scan.api.domain.model.DocumentNotFoundException;
import com.scan.api.domain.model.StorageException;
import com.scan.api.domain.port.in.DocumentUseCase;
import com.scan.api.infrastructure.security.JwtService;
import com.scan.api.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {UploadController.class, AuthController.class})
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "app.auth.username=test-user",
        "app.auth.password-hash=$2b$10$kkze//w20clWp.X1RmThBu2mhsiOI8mdAAn1PKYxPpIqdO52vBjOu",
        "app.jwt.secret=test-secret-test-secret-test-secret-test-secret-0123456789"
})
class UploadControllerTest {

    private static final byte[] PDF = "%PDF-1.4\n%%EOF\n".getBytes();

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @MockitoBean DocumentUseCase documentUseCase;

    private String bearer() {
        return "Bearer " + jwtService.generate("test-user", Map.of("roles", List.of("ROLE_ADMIN")));
    }

    @Test
    void login_retourne_un_token_valide() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-user\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void login_mauvais_mot_de_passe() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-user\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upload_sans_authentification_refuse() throws Exception {
        mvc.perform(multipart("/api/upload").file(new MockMultipartFile("file", "a.pdf", "application/pdf", PDF)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upload_avec_jwt() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentUseCase.savePdf(eq("a.pdf"), eq("application/pdf"), any(InputStream.class), eq((long) PDF.length)))
                .thenReturn(Document.builder().id(id).originalFilename("a.pdf").size(PDF.length).s3Key("k.pdf").build());

        mvc.perform(multipart("/api/upload")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", PDF))
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("a.pdf"))
                .andExpect(jsonPath("$.key").value("k.pdf"));
    }

    @Test
    void upload_fichier_vide() throws Exception {
        mvc.perform(multipart("/api/upload")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]))
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_fichier_invalide_400() throws Exception {
        when(documentUseCase.savePdf(any(), any(), any(), anyLong()))
                .thenThrow(new IllegalArgumentException("Pas un PDF valide"));

        mvc.perform(multipart("/api/upload")
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes()))
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Pas un PDF valide"));
    }

    @Test
    void download_avec_jwt() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentUseCase.download(id))
                .thenReturn(new DocumentUseCase.DownloadResult("mon doc.pdf", "application/pdf", PDF));

        mvc.perform(get("/api/download/{id}", id).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''mon%20doc.pdf")))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void download_inconnu_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentUseCase.download(id)).thenThrow(new DocumentNotFoundException(id));

        mvc.perform(get("/api/download/{id}", id).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void stockage_indisponible_503() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentUseCase.download(id)).thenThrow(new StorageException("boom", null));

        mvc.perform(get("/api/download/{id}", id).header("Authorization", bearer()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void erreur_inattendue_500_sans_details() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentUseCase.download(id)).thenThrow(new IllegalStateException("secret interne"));

        mvc.perform(get("/api/download/{id}", id).header("Authorization", bearer()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("secret"))));
    }

    @Test
    void id_mal_forme_400() throws Exception {
        mvc.perform(get("/api/download/{id}", "pas-un-uuid").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bearer_vide_refuse() throws Exception {
        mvc.perform(get("/api/download/{id}", UUID.randomUUID()).header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwt_invalide_refuse() throws Exception {
        mvc.perform(get("/api/download/{id}", UUID.randomUUID()).header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void route_inconnue_refusee_par_defaut() throws Exception {
        mvc.perform(get("/inconnue")).andExpect(status().isUnauthorized());
        mvc.perform(get("/inconnue").header("Authorization", bearer())).andExpect(status().isForbidden());
    }

    @Test
    void http_basic_desactive() throws Exception {
        String basic = java.util.Base64.getEncoder().encodeToString("test-user:test-password".getBytes());
        mvc.perform(get("/api/download/{id}", UUID.randomUUID()).header("Authorization", "Basic " + basic))
                .andExpect(status().isUnauthorized());
    }
}
