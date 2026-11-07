package com.scan.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test de bout en bout : vraie base Postgres, vrai MinIO (Testcontainers).
 * upload → S3 + documents → download.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext
class DocumentFlowIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    // minio/minio n'est plus publié sur Docker Hub : image Chainguard équivalente
    @Container
    static MinIOContainer minio = new MinIOContainer(
            DockerImageName.parse("cgr.dev/chainguard/minio:latest").asCompatibleSubstituteFor("minio/minio"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.s3.endpoint", minio::getS3URL);
        r.add("app.s3.access-key", minio::getUserName);
        r.add("app.s3.secret-key", minio::getPassword);
        r.add("app.s3.bucket", () -> "scanned-docs");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        r.add("app.auth.username", () -> "test-user");
        r.add("app.auth.password-hash", () -> "$2b$10$kkze//w20clWp.X1RmThBu2mhsiOI8mdAAn1PKYxPpIqdO52vBjOu");
        r.add("app.jwt.secret", () -> "test-secret-test-secret-test-secret-test-secret-0123456789");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired S3Client s3;

    @BeforeAll
    static void createBucket(@Autowired S3Client s3) {
        s3.createBucket(b -> b.bucket("scanned-docs"));
    }

    @Test
    void upload_puis_download() throws Exception {
        String token = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"test-user\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asString();

        byte[] pdf = ("%PDF-1.4\n% " + Instant.now() + "\n%%EOF\n").getBytes();
        var file = new MockMultipartFile("file", "facture.pdf", "application/pdf", pdf);

        JsonNode uploaded = objectMapper.readTree(mvc.perform(multipart("/api/upload").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String id = uploaded.get("id").asString();

        // dédoublonnage : même contenu → même id
        mvc.perform(multipart("/api/upload").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        assertThat(jdbc.queryForObject("select count(*) from documents where sha256 = ?",
                Long.class, sha256Hex(pdf))).isEqualTo(1L);

        // download : bytes identiques depuis MinIO
        mvc.perform(get("/api/download/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pdf));
    }

    @Test
    void image_png_convertie_en_pdf_puis_telechargee() throws Exception {
        String token = jeton();
        String id = envoyer(token, "photo.png", "image/png", unePng());

        mvc.perform(get("/api/download/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("photo.pdf")))
                .andExpect(result -> assertThat(new String(result.getResponse().getContentAsByteArray(), 0, 5,
                        StandardCharsets.US_ASCII)).isEqualTo("%PDF-"));
    }

    @Test
    void image_non_convertible_archivee_en_zip_sans_alteration() throws Exception {
        String token = jeton();
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 contenu-quelconque".getBytes(StandardCharsets.ISO_8859_1);
        String id = envoyer(token, "photo.webp", "image/webp", webp);

        byte[] zip = mvc.perform(get("/api/download/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Content-Disposition", containsString("photo.webp.zip")))
                .andReturn().getResponse().getContentAsByteArray();

        try (var archive = new ZipInputStream(new ByteArrayInputStream(zip))) {
            assertThat(archive.getNextEntry().getName()).isEqualTo("photo.webp");
            assertThat(archive.readAllBytes()).isEqualTo(webp);
        }
    }

    @Test
    void format_non_supporte_refuse() throws Exception {
        mvc.perform(multipart("/api/upload")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()))
                        .header("Authorization", "Bearer " + jeton()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploads_concurrents_du_meme_fichier_un_seul_document_et_un_seul_objet() throws Exception {
        String token = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"test-user\",\"password\":\"test-password\"}"))
                .andReturn().getResponse().getContentAsString()).get("token").asString();

        byte[] pdf = ("%PDF-1.4\n% concurrent " + System.nanoTime() + "\n%%EOF\n").getBytes();
        var file = new MockMultipartFile("file", "c.pdf", "application/pdf", pdf);
        long objectsBefore = s3.listObjectsV2(b -> b.bucket("scanned-docs")).keyCount();

        int n = 8;
        var start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(n)) {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    var res = mvc.perform(multipart("/api/upload").file(file)
                            .header("Authorization", "Bearer " + token)).andReturn().getResponse();
                    assertThat(res.getStatus()).isEqualTo(200);
                    return objectMapper.readTree(res.getContentAsString()).get("id").asString();
                }));
            }
            start.countDown();
            Set<String> ids = new HashSet<>();
            for (Future<String> f : results) ids.add(f.get());
            assertThat(ids).hasSize(1);
        }

        assertThat(jdbc.queryForObject("select count(*) from documents where s3key is not null and sha256 = ?",
                Long.class, sha256Hex(pdf))).isEqualTo(1L);
        // les perdants ont supprimé leur objet : un seul nouvel objet dans le bucket
        assertThat(s3.listObjectsV2(b -> b.bucket("scanned-docs")).keyCount()).isEqualTo(objectsBefore + 1);
    }

    private String jeton() throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"test-user\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asString();
    }

    private String envoyer(String token, String nom, String type, byte[] octets) throws Exception {
        String reponse = mvc.perform(multipart("/api/upload")
                        .file(new MockMultipartFile("file", nom, type, octets))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(reponse).get("id").asString();
    }

    private static byte[] unePng() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private static String sha256Hex(byte[] data) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
    }
}
