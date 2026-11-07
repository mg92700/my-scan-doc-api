package com.scan.api.infrastructure.adapter.in.web;

import com.scan.api.domain.model.Document;
import com.scan.api.domain.model.DocumentPage;
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

    /** Page de résultats : les éléments, le total et les paramètres appliqués. */
    public record PageView(List<DocumentView> items, long total, int offset, int limit) {}

    @GetMapping
    public PageView list(@RequestParam(defaultValue = "false") boolean archived,
                         @RequestParam(defaultValue = "") String q,
                         @RequestParam(defaultValue = "0") int offset,
                         @RequestParam(defaultValue = "20") int limit) {
        DocumentPage page = documentUseCase.list(archived, q, offset, limit);
        return new PageView(page.items().stream().map(DocumentView::from).toList(), page.total(), offset, limit);
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
