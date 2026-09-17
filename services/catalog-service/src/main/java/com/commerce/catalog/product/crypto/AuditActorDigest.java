package com.commerce.catalog.product.crypto;

import java.util.Arrays;
import java.util.Objects;

public record AuditActorDigest(String keyId, byte[] actorHash) {
    public AuditActorDigest(String keyId, byte[] actorHash) {
        this.keyId = Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(actorHash, "actorHash");
        if (actorHash.length != 32) {
            throw new IllegalArgumentException("actorHash must contain exactly 32 bytes");
        }
        this.actorHash = actorHash.clone();
    }

    @Override
    public byte[] actorHash() {
        return actorHash.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AuditActorDigest digest
                && keyId.equals(digest.keyId)
                && Arrays.equals(actorHash, digest.actorHash);
    }

    @Override
    public int hashCode() {
        return 31 * keyId.hashCode() + Arrays.hashCode(actorHash);
    }

    @Override
    public String toString() {
        return "AuditActorDigest[keyId=" + keyId + ", actorHash=REDACTED]";
    }
}
