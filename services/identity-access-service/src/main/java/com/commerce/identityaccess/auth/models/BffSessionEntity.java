package com.commerce.identityaccess.auth.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "bff_session")
public class BffSessionEntity {
    @Id
    @Getter
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "handle_hash", nullable = false, unique = true)
    private byte[] handleHash;

    @Column(name = "csrf_hash", nullable = false)
    @Getter
    private byte[] csrfHash;

    @Column(name = "encryption_key_id", nullable = false)
    @Getter
    private String encryptionKeyId;

    @Column(name = "token_bundle_ciphertext", nullable = false)
    private byte[] tokenBundleCiphertext;

    @Column(name = "principal_kind", nullable = false)
    @Getter
    private String principalKind;

    @Column(name = "issuer", nullable = false)
    @Getter
    private String issuer;

    @Column(name = "subject", nullable = false)
    @Getter
    private String subject;

    @Column(name = "account_id")
    @Getter
    private @Nullable UUID accountId;

    @Column(name = "security_epoch")
    @Getter
    private @Nullable Long securityEpoch;

    @Column(name = "oidc_session_id")
    private @Nullable String oidcSessionId;

    @Column(name = "authenticated_at", nullable = false)
    @Getter
    private Instant authenticatedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "idle_expires_at", nullable = false)
    @Getter
    private Instant idleExpiresAt;

    @Column(name = "absolute_expires_at", nullable = false)
    @Getter
    private Instant absoluteExpiresAt;

    @Column(name = "status", nullable = false)
    @Getter
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
            @Nullable UUID accountId,
            @Nullable Long securityEpoch,
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
        this.accountId = accountId;
        this.securityEpoch = securityEpoch;
        this.oidcSessionId = oidcSessionId;
        this.authenticatedAt = authenticatedAt;
        this.lastSeenAt = authenticatedAt;
        this.idleExpiresAt = idleExpiresAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
        this.status = "ACTIVE";
    }

    public byte[] getTokenBundleCiphertext() {
        return tokenBundleCiphertext.clone();
    }

    public void touch(Instant now, Instant newIdleExpiry) {
        this.lastSeenAt = now;
        this.idleExpiresAt = newIdleExpiry;
    }
}
