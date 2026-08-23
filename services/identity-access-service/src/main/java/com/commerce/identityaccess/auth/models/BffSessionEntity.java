package com.commerce.identityaccess.auth.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "bff_session")
public class BffSessionEntity {
    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "handle_hash", nullable = false, unique = true)
    private byte[] handleHash;

    @Column(name = "csrf_hash", nullable = false)
    private byte[] csrfHash;

    @Column(name = "encryption_key_id", nullable = false)
    private String encryptionKeyId;

    @Column(name = "token_bundle_ciphertext", nullable = false)
    private byte[] tokenBundleCiphertext;

    @Column(name = "principal_kind", nullable = false)
    private String principalKind;

    @Column(name = "issuer", nullable = false)
    private String issuer;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "oidc_session_id")
    private @Nullable String oidcSessionId;

    @Column(name = "authenticated_at", nullable = false)
    private Instant authenticatedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "idle_expires_at", nullable = false)
    private Instant idleExpiresAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "status", nullable = false)
    private String status;

    protected BffSessionEntity() {}

    public BffSessionEntity(
            UUID sessionId,
            byte[] handleHash,
            byte[] csrfHash,
            String encryptionKeyId,
            byte[] tokenBundleCiphertext,
            PrincipalKind principalKind,
            String issuer,
            String subject,
            @Nullable String oidcSessionId,
            Instant authenticatedAt,
            Instant idleExpiresAt,
            Instant absoluteExpiresAt) {
        this.sessionId = sessionId;
        this.handleHash = handleHash;
        this.csrfHash = csrfHash;
        this.encryptionKeyId = encryptionKeyId;
        this.tokenBundleCiphertext = tokenBundleCiphertext;
        this.principalKind = principalKind.name();
        this.issuer = issuer;
        this.subject = subject;
        this.oidcSessionId = oidcSessionId;
        this.authenticatedAt = authenticatedAt;
        this.lastSeenAt = authenticatedAt;
        this.idleExpiresAt = idleExpiresAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
        this.status = "ACTIVE";
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public byte[] getCsrfHash() {
        return csrfHash;
    }

    public String getPrincipalKind() {
        return principalKind;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getSubject() {
        return subject;
    }

    public Instant getAuthenticatedAt() {
        return authenticatedAt;
    }

    public Instant getIdleExpiresAt() {
        return idleExpiresAt;
    }

    public Instant getAbsoluteExpiresAt() {
        return absoluteExpiresAt;
    }

    public String getStatus() {
        return status;
    }

    public void touch(Instant now, Instant newIdleExpiry) {
        this.lastSeenAt = now;
        this.idleExpiresAt = newIdleExpiry;
    }
}
