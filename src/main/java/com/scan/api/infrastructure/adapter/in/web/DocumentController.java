package com.scan.api.infrastructure.adapter.in.web;

import com.scan.api.domain.model.Document;
import com.scan.api.domain.port.in.DocumentUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Gestion des documents déjà stockés : consultation, archivage, suppression.
 * Le téléchargement reste dans {@link UploadController} ({@code GET /api/download/{id}}).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentUseCase documentUseCase;

    /** Vue exposée : jamais la clé de stockage ni le SHA-256. */
    public record DocumentView(UUID id, String name, long size, String contentType,
                               Instant createdAt, Instant archivedAt) {
        static DocumentView from(Document d) {
            return new DocumentView(d.getId(), d.getOriginalFilename(), d.getSize(),
                    d.getContentType(), d.getCreatedAt(), d.getArchivedAt());
        }
    }

    @GetMapping
    public List<DocumentView> list(@RequestParam(defaultValue = "false") boolean archived) {
        return documentUseCase.list(archived).stream().map(DocumentView::from).toList();
    }

    @PostMapping("/{id}/archive")
    public DocumentView archive(@PathVariable UUID id) {
        return DocumentView.from(documentUseCase.archive(id));
    }

    @PostMapping("/{id}/unarchive")
    public DocumentView unarchive(@PathVariable UUID id) {
        return DocumentView.from(documentUseCase.unarchive(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }
}
