// src/test/java/com/scan/api/web/UploadControllerTest.java
package com.scan.api.web;

import com.scan.api.domain.Document;
import com.scan.api.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UploadController.class)
class UploadControllerTest {

    @Autowired MockMvc mvc;

    @MockBean DocumentService documentService; // <-- interface mockée

    @Test
    void upload_ok_returns200_and_json() throws Exception {
        byte[] pdf = "%PDF-1.7\n%âãÏÓ\n".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", MediaType.APPLICATION_PDF_VALUE, pdf);

        Document doc = Document.builder()
                .id(UUID.randomUUID())
                .originalFilename("test.pdf")
                .size(pdf.length)
                .contentType(MediaType.APPLICATION_PDF_VALUE)
                .sha256("abc")
                .s3Key("2025/08/14/uuid.pdf")
                .createdAt(Instant.now())
                .build();

        given(documentService.savePdf(
                ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.anyLong()
        )).willReturn(doc);

        mvc.perform(multipart("/api/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(doc.getId().toString()))
                .andExpect(jsonPath("$.name").value("test.pdf"))
                .andExpect(jsonPath("$.size").value(pdf.length))
                .andExpect(jsonPath("$.key").value("2025/08/14/uuid.pdf"));
    }

    @Test
    void upload_missingFile_returns400() throws Exception {
        mvc.perform(multipart("/api/upload"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void download_ok_streams_bytes_with_headers() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] content = "%PDF-1.7\nfake\n".getBytes();

        given(documentService.download(id))
                .willReturn(new DocumentService.DownloadResult(
                        "mydoc.pdf", MediaType.APPLICATION_PDF_VALUE, content));

        mvc.perform(get("/api/download/{id}", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes(content));
    }

    @Test
    void download_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(documentService.download(id))
                .willThrow(new IllegalArgumentException("Document introuvable"));

        mvc.perform(get("/api/download/{id}", id))
                .andExpect(status().isNotFound());
    }
}
