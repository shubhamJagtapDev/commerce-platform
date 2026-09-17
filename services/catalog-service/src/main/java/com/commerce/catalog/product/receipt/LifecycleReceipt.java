package com.commerce.catalog.product.receipt;

import com.commerce.catalog.product.application.CommandExecutionResult;
import com.commerce.catalog.product.application.CommandOutcome;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public final class LifecycleReceipt {
    private final String hashKeyId;
    private final byte[] keyHash;
    private final byte[] requestFingerprint;
    private final CommandOutcome outcome;
    private final int originalHttpStatus;
    private final Instant expiresAt;

    public LifecycleReceipt(
            String hashKeyId,
            byte[] keyHash,
            byte[] requestFingerprint,
            CommandOutcome outcome,
            int originalHttpStatus,
            Instant expiresAt) {
        this.hashKeyId = Objects.requireNonNull(hashKeyId, "hashKeyId");
        this.keyHash = requireDigest("keyHash", keyHash);
        this.requestFingerprint = requireDigest("requestFingerprint", requestFingerprint);
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        if (originalHttpStatus != 200 && originalHttpStatus != 201) {
            throw new IllegalArgumentException("originalHttpStatus must be 200 or 201");
        }
        this.originalHttpStatus = originalHttpStatus;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(outcome.appliedAt())) {
            throw new IllegalArgumentException("expiresAt must be after appliedAt");
        }
    }

    public String hashKeyId() {
        return hashKeyId;
    }

    public byte[] keyHash() {
        return keyHash.clone();
    }

    public byte[] requestFingerprint() {
        return requestFingerprint.clone();
    }

    public CommandOutcome outcome() {
        return outcome;
    }

    public int originalHttpStatus() {
        return originalHttpStatus;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public CommandExecutionResult replayResult() {
        return new CommandExecutionResult(outcome, originalHttpStatus, true);
    }

    @Override
    public String toString() {
        return "LifecycleReceipt[hashKeyId=" + hashKeyId + ", outcome=" + outcome + ", digests=REDACTED]";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LifecycleReceipt receipt
                && hashKeyId.equals(receipt.hashKeyId)
                && Arrays.equals(keyHash, receipt.keyHash)
                && Arrays.equals(requestFingerprint, receipt.requestFingerprint)
                && outcome.equals(receipt.outcome)
                && originalHttpStatus == receipt.originalHttpStatus
                && expiresAt.equals(receipt.expiresAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(hashKeyId, outcome, originalHttpStatus, expiresAt);
        result = 31 * result + Arrays.hashCode(keyHash);
        return 31 * result + Arrays.hashCode(requestFingerprint);
    }

    private static byte[] requireDigest(String name, byte[] digest) {
        Objects.requireNonNull(digest, name);
        if (digest.length != 32) {
            throw new IllegalArgumentException(name + " must contain exactly 32 bytes");
        }
        return digest.clone();
    }
}
