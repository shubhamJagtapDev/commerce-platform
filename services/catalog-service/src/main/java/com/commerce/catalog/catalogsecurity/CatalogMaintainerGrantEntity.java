package com.commerce.catalog.catalogsecurity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "catalog_maintainer_grant")
class CatalogMaintainerGrantEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "issuer", nullable = false)
    private String issuer;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "revoked_at")
    private @Nullable Instant revokedAt;

    protected CatalogMaintainerGrantEntity() {}

    CatalogMaintainerGrantEntity(UUID id, String issuer, String subject, Instant now) {
        this.id = id;
        this.issuer = issuer;
        this.subject = subject;
        this.status = "ACTIVE";
        this.version = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID getId() {
        return id;
    }
}
