package com.scan.api.web;

import com.scan.api.domain.Document;
import com.scan.api.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class UploadController {

    private final DocumentService documentService;

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestPart("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("Fichier manquant.");
        }
        Document doc = documentService.savePdf(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                file.getSize()
        );
        return ResponseEntity.ok(Map.of(
                "id", doc.getId().toString(),
                "name", doc.getOriginalFilename(),
                "size", doc.getSize(),
                "key", doc.getS3Key()
        ));
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(@PathVariable("id") UUID id) {
        var res = documentService.download(id);
        String filename = res.filename() == null ? "document.pdf" : res.filename();
        String dispo = "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(res.contentType() == null ? MediaType.APPLICATION_PDF_VALUE : res.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, dispo)
                .body(res.bytes());
    }
}


