package com.commerce.catalog.catalogsecurity.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "catalog_command_idempotency")
public class CatalogCommandIdempotencyEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "issuer", nullable = false)
    private String issuer;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "operation_code", nullable = false)
    private String operationCode;

    @Column(name = "key_hash", nullable = false)
    private byte[] keyHash;

    @Column(name = "request_fingerprint", nullable = false)
    private byte[] requestFingerprint;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "result_probe_id")
    private @Nullable UUID resultProbeId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private @Nullable Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected CatalogCommandIdempotencyEntity() {}

    public CatalogCommandIdempotencyEntity(
            UUID id,
            String issuer,
            String subject,
            String operationCode,
            byte[] keyHash,
            byte[] requestFingerprint,
            Instant createdAt,
            Instant expiresAt) {
        this.id = id;
        this.issuer = issuer;
        this.subject = subject;
        this.operationCode = operationCode;
        this.keyHash = keyHash.clone();
        this.requestFingerprint = requestFingerprint.clone();
        this.status = "IN_PROGRESS";
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public byte[] getRequestFingerprint() {
        return requestFingerprint.clone();
    }

    public boolean completed() {
        return "COMPLETED".equals(status) && resultProbeId != null;
    }

    public boolean expiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    @Nullable
    public UUID getResultProbeId() {
        return resultProbeId;
    }

    public void complete(UUID probeId, Instant now) {
        status = "COMPLETED";
        resultProbeId = probeId;
        completedAt = now;
    }

    public void restart(byte[] newRequestFingerprint, Instant now, Instant newExpiresAt) {
        requestFingerprint = newRequestFingerprint.clone();
        status = "IN_PROGRESS";
        resultProbeId = null;
        createdAt = now;
        completedAt = null;
        expiresAt = newExpiresAt;
    }
}
