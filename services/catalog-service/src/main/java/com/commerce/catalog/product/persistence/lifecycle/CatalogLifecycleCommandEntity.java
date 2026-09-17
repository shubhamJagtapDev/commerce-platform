package com.commerce.catalog.product.persistence.lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "catalog_lifecycle_command")
public class CatalogLifecycleCommandEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "grant_id", nullable = false)
    private UUID grantId;

    @Column(name = "operation_code", nullable = false, length = 32)
    private String operationCode;

    @Column(name = "hash_key_id", nullable = false, length = 64)
    private String hashKeyId;

    @Column(name = "key_hash", nullable = false)
    private byte[] keyHash;

    @Column(name = "request_fingerprint", nullable = false)
    private byte[] requestFingerprint;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "variant_id")
    private @Nullable UUID variantId;

    @Column(name = "result_version", nullable = false)
    private long resultVersion;

    @Column(name = "result_state", nullable = false, length = 16)
    private String resultState;

    @Column(name = "publication_id")
    private @Nullable UUID publicationId;

    @Column(name = "http_status", nullable = false)
    private short httpStatus;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected CatalogLifecycleCommandEntity() {}

    CatalogLifecycleCommandEntity(
            UUID id,
            UUID grantId,
            String operationCode,
            String hashKeyId,
            byte[] keyHash,
            byte[] requestFingerprint,
            UUID productId,
            @Nullable UUID variantId,
            long resultVersion,
            String resultState,
            @Nullable UUID publicationId,
            short httpStatus,
            Instant appliedAt,
            Instant expiresAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.grantId = Objects.requireNonNull(grantId, "grantId");
        this.operationCode = Objects.requireNonNull(operationCode, "operationCode");
        this.hashKeyId = Objects.requireNonNull(hashKeyId, "hashKeyId");
        this.keyHash = keyHash.clone();
        this.requestFingerprint = requestFingerprint.clone();
        this.productId = Objects.requireNonNull(productId, "productId");
        this.variantId = variantId;
        this.resultVersion = resultVersion;
        this.resultState = Objects.requireNonNull(resultState, "resultState");
        this.publicationId = publicationId;
        this.httpStatus = httpStatus;
        this.appliedAt = Objects.requireNonNull(appliedAt, "appliedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    UUID id() {
        return id;
    }

    String operationCode() {
        return operationCode;
    }

    String hashKeyId() {
        return hashKeyId;
    }

    byte[] keyHash() {
        return keyHash.clone();
    }

    byte[] requestFingerprint() {
        return requestFingerprint.clone();
    }

    UUID productId() {
        return productId;
    }

    @Nullable
    UUID variantId() {
        return variantId;
    }

    long resultVersion() {
        return resultVersion;
    }

    String resultState() {
        return resultState;
    }

    @Nullable
    UUID publicationId() {
        return publicationId;
    }

    int httpStatus() {
        return httpStatus;
    }

    Instant appliedAt() {
        return appliedAt;
    }

    Instant expiresAt() {
        return expiresAt;
    }
}
