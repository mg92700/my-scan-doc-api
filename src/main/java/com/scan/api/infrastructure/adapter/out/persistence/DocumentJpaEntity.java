package com.scan.api.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Entité JPA — uniquement dans l'infra.
 * Le domaine ne voit jamais cette classe.
 * Le mapping domaine ↔ JPA est fait dans DocumentJpaAdapter.
 */
@Entity
@Table(name = "documents")
@Getter @Setter
@Builder @NoArgsConstructor @AllArgsConstructor
class DocumentJpaEntity {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false, length = 64, unique = true)
    private String sha256;

    @Column(nullable = false, length = 500)
    private String s3Key;

    @Column(nullable = false)
    private Instant createdAt;
}
