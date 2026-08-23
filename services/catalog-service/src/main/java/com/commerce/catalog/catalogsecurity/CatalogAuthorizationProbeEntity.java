package com.commerce.catalog.catalogsecurity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_authorization_probe")
class CatalogAuthorizationProbeEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "grant_id", nullable = false)
    private UUID grantId;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "committed_at", nullable = false)
    private Instant committedAt;

    protected CatalogAuthorizationProbeEntity() {}

    CatalogAuthorizationProbeEntity(UUID id, UUID grantId, Instant committedAt) {
        this.id = id;
        this.grantId = grantId;
        this.version = 0;
        this.committedAt = committedAt;
    }

    UUID getId() {
        return id;
    }

    long getVersion() {
        return version;
    }

    Instant getCommittedAt() {
        return committedAt;
    }
}
