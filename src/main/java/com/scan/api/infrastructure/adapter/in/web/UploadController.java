package com.scan.api.infrastructure.adapter.in.web;

import com.scan.api.domain.model.Document;
import com.scan.api.domain.port.in.DocumentUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter entrant (driving adapter) — expose l'API REST.
 * Dépend uniquement de DocumentUseCase (port), jamais du service concret.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class UploadController {

    private final DocumentUseCase documentUseCase;

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestPart("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("Fichier manquant.");
        }
        Document doc = documentUseCase.save(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                file.getSize()
        );
        // contentType = type réellement stocké (application/pdf, ou application/zip si l'image n'a pas pu être convertie)
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", doc.getId().toString());
        body.put("name", doc.getOriginalFilename());
        body.put("size", doc.getSize());
        body.put("key", doc.getS3Key());
        body.put("contentType", doc.getContentType());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        var res = documentUseCase.download(id);
        String filename = res.filename() == null ? "document.pdf" : res.filename();
        String dispo = "attachment; filename*=UTF-8''" +
                URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"); // RFC 5987
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        res.contentType() == null ? MediaType.APPLICATION_PDF_VALUE : res.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, dispo)
                .body(res.bytes());
    }
}
