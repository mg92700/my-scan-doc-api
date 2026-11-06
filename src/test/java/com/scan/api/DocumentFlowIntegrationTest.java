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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
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
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    static void createBucket(@Autowired S3Client s3) {
        s3.createBucket(b -> b.bucket("scanned-docs"));
    }

    @Test
    void upload_puis_download() throws Exception {
        String token = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
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
        assertThat(jdbc.queryForObject("select count(*) from documents", Long.class)).isEqualTo(1L);

        // download : bytes identiques depuis MinIO
        mvc.perform(get("/api/download/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pdf));
    }
}
