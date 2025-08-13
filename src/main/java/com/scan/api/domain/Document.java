package com.scan.api.domain;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter @Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class Document {
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
