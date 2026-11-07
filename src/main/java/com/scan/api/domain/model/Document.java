package com.scan.api.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Entité domaine pure — aucune annotation JPA, aucune dépendance framework.
 * Le mapping vers la base de données est géré par DocumentJpaEntity dans l'infra.
 */
public class Document {

    private UUID id;
    private String originalFilename;
    private long size;
    private String contentType;
    private String sha256;
    private String s3Key;
    private Instant createdAt;
    private Instant archivedAt;

    public Document() {}

    public Document(UUID id, String originalFilename, long size,
                    String contentType, String sha256, String s3Key, Instant createdAt) {
        this.id = id;
        this.originalFilename = originalFilename;
        this.size = size;
        this.contentType = contentType;
        this.sha256 = sha256;
        this.s3Key = s3Key;
        this.createdAt = createdAt;
    }

    // --- Getters ---
    public UUID getId()               { return id; }
    public String getOriginalFilename() { return originalFilename; }
    public long getSize()             { return size; }
    public String getContentType()    { return contentType; }
    public String getSha256()         { return sha256; }
    public String getS3Key()          { return s3Key; }
    public Instant getCreatedAt()     { return createdAt; }
    /** Date d'archivage ; {@code null} tant que le document n'est pas archivé. */
    public Instant getArchivedAt()    { return archivedAt; }

    // --- Setters (pour le builder) ---
    public void setId(UUID id)                          { this.id = id; }
    public void setOriginalFilename(String name)        { this.originalFilename = name; }
    public void setSize(long size)                      { this.size = size; }
    public void setContentType(String contentType)      { this.contentType = contentType; }
    public void setSha256(String sha256)                { this.sha256 = sha256; }
    public void setS3Key(String s3Key)                  { this.s3Key = s3Key; }
    public void setCreatedAt(Instant createdAt)         { this.createdAt = createdAt; }
    public void setArchivedAt(Instant archivedAt)       { this.archivedAt = archivedAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Document d = new Document();
        public Builder id(UUID id)                       { d.id = id; return this; }
        public Builder originalFilename(String name)     { d.originalFilename = name; return this; }
        public Builder size(long size)                   { d.size = size; return this; }
        public Builder contentType(String ct)            { d.contentType = ct; return this; }
        public Builder sha256(String sha)                { d.sha256 = sha; return this; }
        public Builder s3Key(String key)                 { d.s3Key = key; return this; }
        public Builder createdAt(Instant ts)             { d.createdAt = ts; return this; }
        public Builder archivedAt(Instant ts)            { d.archivedAt = ts; return this; }
        public Document build()                          { return d; }
    }
}
