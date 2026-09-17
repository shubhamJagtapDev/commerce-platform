package com.commerce.catalog.product.application;

import java.util.Arrays;
import java.util.Objects;

public final class PreparedLifecycleDigest {
    private static final int SHA_256_BYTES = 32;

    private final String hashKeyId;
    private final byte[] keyHash;
    private final byte[] requestFingerprint;

    public PreparedLifecycleDigest(String hashKeyId, byte[] keyHash, byte[] requestFingerprint) {
        this.hashKeyId = Objects.requireNonNull(hashKeyId, "hashKeyId");
        if (hashKeyId.isBlank() || hashKeyId.length() > 64) {
            throw new IllegalArgumentException("hashKeyId must contain 1 to 64 nonblank characters");
        }
        this.keyHash = requireDigest("keyHash", keyHash);
        this.requestFingerprint = requireDigest("requestFingerprint", requestFingerprint);
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

    @Override
    public String toString() {
        return "PreparedLifecycleDigest[hashKeyId=" + hashKeyId + ", digests=REDACTED]";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PreparedLifecycleDigest digest
                && hashKeyId.equals(digest.hashKeyId)
                && Arrays.equals(keyHash, digest.keyHash)
                && Arrays.equals(requestFingerprint, digest.requestFingerprint);
    }

    @Override
    public int hashCode() {
        int result = hashKeyId.hashCode();
        result = 31 * result + Arrays.hashCode(keyHash);
        return 31 * result + Arrays.hashCode(requestFingerprint);
    }

    private static byte[] requireDigest(String name, byte[] digest) {
        Objects.requireNonNull(digest, name);
        if (digest.length != SHA_256_BYTES) {
            throw new IllegalArgumentException(name + " must contain exactly 32 bytes");
        }
        return digest.clone();
    }
}
